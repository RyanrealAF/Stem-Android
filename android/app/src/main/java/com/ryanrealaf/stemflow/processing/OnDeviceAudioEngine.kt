package com.ryanrealaf.stemflow.processing

import ai.onnxruntime.*
import android.content.Context
import android.media.*
import android.net.Uri
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.*
import kotlin.math.*

private const val DEMUCS_URL="https://huggingface.co/StemSplitio/htdemucs-onnx/resolve/main/htdemucs_fp16weights.onnx"
private const val PITCH_URL="https://huggingface.co/daserge/basic-pitch-onnx/resolve/main/nmp.onnx"

private class Models(c:Context){
 private val d=File(c.filesDir,"models").apply{mkdirs()}
 fun get(name:String,url:String,cb:(Long,Long)->Unit):File{
  val f=File(d,name); if(f.length()>1024)return f
  val t=File(d,".$name.part"); val x=URL(url).openConnection() as HttpURLConnection
  x.connectTimeout=20000;x.readTimeout=120000;x.instanceFollowRedirects=true
  if(x.responseCode !in 200..299)throw IOException("Model HTTP "+x.responseCode)
  val total=x.contentLengthLong
  x.inputStream.buffered().use{ins->FileOutputStream(t).use{out->
   val b=ByteArray(65536);var n=0L;while(true){val k=ins.read(b);if(k<0)break;out.write(b,0,k);n+=k;cb(n,total)}
  }}
  x.disconnect();if(!t.renameTo(f)){t.copyTo(f,true);t.delete()};return f
 }
}

data class Pcm(val ch:Array<FloatArray>,val rate:Int){val n get()=ch[0].size}

private class Decode {
 fun read(c:Context,u:Uri):Pcm{
  val e=MediaExtractor();e.setDataSource(c,u,null);var ti=-1;lateinit var fmt:MediaFormat
  for(i in 0 until e.trackCount){val f=e.getTrackFormat(i);if((f.getString(MediaFormat.KEY_MIME)?:"").startsWith("audio/")){ti=i;fmt=f;break}}
  require(ti>=0){"No audio track"};e.selectTrack(ti)
  val mc=MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!);mc.configure(fmt,null,null,0);mc.start()
  val chunks=ArrayList<ShortArray>();val bi=MediaCodec.BufferInfo();var inDone=false;var outDone=false
  try{while(!outDone){
   if(!inDone){val ii=mc.dequeueInputBuffer(10000);if(ii>=0){val b=mc.getInputBuffer(ii)!!;b.clear();val z=e.readSampleData(b,0)
    if(z<0){mc.queueInputBuffer(ii,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inDone=true}else{mc.queueInputBuffer(ii,0,z,e.sampleTime,0);e.advance()}}}
   val oi=mc.dequeueOutputBuffer(bi,10000);if(oi>=0){val b=mc.getOutputBuffer(oi);if(b!=null&&bi.size>0){b.position(bi.offset);b.limit(bi.offset+bi.size);val raw=ByteArray(bi.size);b.get(raw);val s=ShortArray(raw.size/2);ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s);chunks.add(s)}
    mc.releaseOutputBuffer(oi,false);if((bi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)outDone=true}}
  }finally{runCatching{mc.stop()};mc.release();e.release()}
  val rate=fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);val nc=fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);val total=chunks.sumOf{it.size};require(total>0){"Decoder returned no PCM"}
  val all=ShortArray(total);var q=0;for(s in chunks){s.copyInto(all,q);q+=s.size};val frames=total/nc
  val out=Array(2){FloatArray(frames)};for(i in 0 until frames){out[0][i]=all[i*nc]/32768f;out[1][i]=all[i*nc+min(1,nc-1)]/32768f}
  return Pcm(out,rate)
 }
 fun resample(p:Pcm,target:Int):Pcm{if(p.rate==target)return p;val m=max(1,(p.n.toLong()*target/p.rate).toInt());val o=Array(2){FloatArray(m)};val r=p.rate.toDouble()/target
  for(i in 0 until m){val xx=i*r;val j=min(xx.toInt(),p.n-1);val k=min(j+1,p.n-1);val f=(xx-j).toFloat();for(c in 0..1)o[c][i]=p.ch[c][j]*(1-f)+p.ch[c][k]*f};return Pcm(o,target)}
}

private fun wav(f:File,a:Array<FloatArray>,sr:Int){val n=a[0].size;FileOutputStream(f).use{o->
 fun w16(v:Int){o.write(v and 255);o.write(v ushr 8 and 255)};fun w32(v:Int){w16(v);w16(v ushr 16)}
 val bytes=n*a.size*2;o.write("RIFF".toByteArray());w32(36+bytes);o.write("WAVEfmt ".toByteArray());w32(16);w16(1);w16(a.size);w32(sr);w32(sr*a.size*2);w16(a.size*2);w16(16);o.write("data".toByteArray());w32(bytes)
 val b=ByteArray(2);for(i in 0 until n)for(c in a.indices){val v=(a[c][i].coerceIn(-1f,1f)*32767).toInt();b[0]=(v and 255).toByte();b[1]=(v ushr 8 and 255).toByte();o.write(b)}
}}

class NeuralStemSeparator(private val c:Context):StemSeparator{
 private val dec=Decode();private val models=Models(c);private val env=OrtEnvironment.getEnvironment();private val names=arrayOf("drums","bass","other","vocals")
 override fun separate(input:Uri,out:File,progress:(Float,String)->Unit){
  out.mkdirs();val model=models.get("htdemucs_fp16weights.onnx",DEMUCS_URL){n,t->progress(if(t>0).08f*n/t else .03f,"Downloading Demucs model…")}
  val a=dec.resample(dec.read(c,input),44100);val n=343980;val ov=n/4;val stride=n-ov;val count=max(1,(a.n+stride-1)/stride)
  val opt=OrtSession.SessionOptions().apply{setIntraOpNumThreads(2)};val s=env.createSession(model.path,opt);val y=Array(4){Array(2){FloatArray(a.n)}};val wt=FloatArray(a.n)
  try{for(z in 0 until count){val st=z*stride;val en=min(st+n,a.n);val len=en-st;val xx=FloatArray(2*n);for(ch in 0..1)a.ch[ch].copyInto(xx,ch*n,st,en)
   val t=OnnxTensor.createTensor(env,FloatBuffer.wrap(xx),longArrayOf(1,2,n.toLong()));s.run(mapOf("mix" to t)).use{r->
    @Suppress("UNCHECKED_CAST") val v=r.get(0).value as Array<Array<Array<FloatArray>>>
    for(src in 0..3)for(ch in 0..1)for(i in 0 until len){val w=when{ i<ov->i.toFloat()/ov; i>=n-ov->(n-1-i).toFloat()/ov;else->1f};y[src][ch][st+i]+=v[0][src][ch][i]*w}
   };t.close();for(i in st until en){val local=i-st;val w=when{local<ov->local.toFloat()/ov;local>=n-ov->(n-1-local).toFloat()/ov;else->1f};wt[i]+=w};progress(.08f+.72f*(z+1f)/count,"Separating "+(z+1)+"/"+count+"…")}
  }finally{s.close();opt.close()}
  for(src in 0..3){for(ch in 0..1)for(i in 0 until a.n)y[src][ch][i]/=max(wt[i],1e-8f);wav(File(out,names[src]+".wav"),y[src],44100)}
  progress(.85f,"Real neural stems ready")
 }
}

class NeuralPitchTranscriber(private val c:Context):StemTranscriber{
 private val dec=Decode();private val models=Models(c);private val env=OrtEnvironment.getEnvironment();private val sr=22050;private val hop=256;private val win=43744;private val overlap=7680;private val stride=win-overlap
 override fun transcribe(stem:File,midi:File,progress:(Float,String)->Unit){
  val model=models.get("nmp.onnx",PITCH_URL){n,t->progress(if(t>0).08f*n/t else .02f,"Downloading Basic Pitch model…")}
  val p=dec.resample(dec.read(c,Uri.fromFile(stem)),sr);val mono=FloatArray(p.n){(p.ch[0][it]+p.ch[1][it])*.5f};val input=FloatArray(mono.size+overlap/2);mono.copyInto(input,overlap/2)
  val opt=OrtSession.SessionOptions().apply{setIntraOpNumThreads(2)};val s=env.createSession(model.path,opt);val fw=ArrayList<Array<FloatArray>>();val ow=ArrayList<Array<FloatArray>>()
  try{var pos=0;while(pos<input.size){val w=FloatArray(win);input.copyInto(w,0,pos,min(pos+win,input.size));val t=OnnxTensor.createTensor(env,FloatBuffer.wrap(w),longArrayOf(1,win.toLong(),1))
   s.run(mapOf("serving_default_input_2:0" to t)).use{r->@Suppress("UNCHECKED_CAST") val f=r.get("StatefulPartitionedCall:1").get().getValue() as Array<Array<FloatArray>>;@Suppress("UNCHECKED_CAST") val o=r.get("StatefulPartitionedCall:2").get().getValue() as Array<Array<FloatArray>>;fw.add(f[0]);ow.add(o[0])};t.close();pos+=stride;progress(.08f+.45f*min(1f,pos.toFloat()/input.size),"Basic Pitch inference…")}
  }finally{s.close();opt.close()}
  val frames=unwrap(fw,mono.size);val onsets=unwrap(ow,mono.size);val notes=ArrayList<PitchNote>()
  for(pitch in 0 until 88){var i=1;while(i<onsets.size-1){if(onsets[i][pitch]>=.5f&&onsets[i][pitch]>=onsets[i-1][pitch]&&onsets[i][pitch]>=onsets[i+1][pitch]){var e=i+1;var gaps=0;while(e<frames.size-1&&gaps<11){if(frames[e][pitch]<.3f)gaps++ else gaps=0;e++};e-=gaps;if(e-i>=5){var sum=0f;for(k in i until e)sum+=frames[k][pitch];notes.add(PitchNote(pitch+21,i*hop/sr.toFloat(),max(hop/sr.toFloat(),(e-i)*hop/sr.toFloat()),(sum/max(1,e-i)*127).toInt().coerceIn(1,127)))};i=e}else i++}}
  require(notes.isNotEmpty()){"Basic Pitch produced no notes"};StandardMidiWriter().writeMidi(notes,midi);progress(1f,"Real MIDI written: "+notes.size+" notes")
 }
 private fun unwrap(w:List<Array<FloatArray>>,samples:Int):Array<FloatArray>{val rows=ArrayList<FloatArray>();for(x in w)for(i in 15 until x.size-15)rows.add(x[i]);val expected=ceil(samples.toDouble()/stride).toInt()*(172-30);return rows.take(min(expected,rows.size)).toTypedArray()}
}
