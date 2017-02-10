package com.nao20010128nao.Phosphorus.phar

import com.google.common.primitives.Bytes
import com.nao20010128nao.Phosphorus.phar.events.*
import org.apache.commons.codec.binary.Hex

/**
 * Created by nao on 2017/02/09.
 */
class PharParser implements Iterable<PharParserEvent>{
    static final byte[] STUB_FINAL_A="?>".bytes
    static final byte[] STUB_FINAL_B="__HALT_COMPILER();".bytes
    static final byte[] STUB_FINAL_C="__halt_compiler();".bytes

    private File f
    List<PharParserEvent> list

    PharParser(File f){
        this.f=f
    }

    void readAll(){
        list=[]
        list+=new BeginEvent()
        //find stub
        def channel=null
        int stubEnd=-1
        try {
            channel=new RandomAccessFile(f,"r")
            byte[] tmp=new byte[STUB_FINAL_C.length+20]
            for(long i=0;i<(channel.length()-STUB_FINAL_C.length-20);i++){
                channel.seek i
                channel.read tmp

                stubEnd=Bytes.indexOf(tmp,STUB_FINAL_A)
                if(stubEnd!=-1){
                    stubEnd=stubEnd+i+STUB_FINAL_A.length
                    break
                }
                stubEnd=Bytes.indexOf(tmp,STUB_FINAL_B)
                if(stubEnd!=-1){
                    stubEnd=stubEnd+i+STUB_FINAL_B.length
                }
                stubEnd=Bytes.indexOf(tmp,STUB_FINAL_C)
                if(stubEnd!=-1){
                    stubEnd=stubEnd+i+STUB_FINAL_C.length
                }
            }
            channel.seek stubEnd
            channel.read tmp,0,2
            def tmpStr=new String(tmp,0,2)
            if(tmpStr.startsWith("\r\n")){
                stubEnd+=2
            }else if(tmpStr.startsWith("\r")){
                stubEnd++
            }else if(tmpStr.startsWith("\n")){
                stubEnd++
            }
        } finally {
            if(channel!=null)channel.close()
        }


        def buffer=new LocalFileReader(f)
        if(stubEnd!=-1){
            byte[] readBuf=new byte[stubEnd]
            buffer.get readBuf
            list+=new StubEvent(readBuf)
        }
        //read manifest in LITTLE ENDIAN
        int manifestTotal=buffer.leInt
        int filesInsidePhar=buffer.leInt
        short pharApiVersion=buffer.short
        int globalFlags=buffer.leInt
        int pharAliasesLength=buffer.leInt
        byte[] pharAliases=new byte[pharAliasesLength]
        buffer.get pharAliases
        int pharMetadataLength=buffer.leInt
        byte[] pharMetadata=new byte[pharMetadataLength]
        buffer.get pharMetadata
        list+=new ManifestEvent(
                manifestTotal,
                filesInsidePhar,
                pharApiVersion,
                globalFlags,
                pharAliasesLength,
                pharAliases,
                pharMetadataLength,
                pharMetadata
        )
        //read file manifest
        List<FileManifestEvent> files=[]
        filesInsidePhar.times {
            int fileNameLength=buffer.leInt
            byte[] fileName=new byte[fileNameLength]
            buffer.get fileName
            int nonCompressedFileSize=buffer.leInt
            int unixFileTimeStamp=buffer.leInt
            int compressedFileSize=buffer.leInt
            int crc32Checksum=buffer.leInt
            int bitFlags=buffer.leInt
            int fileMetadataLength=buffer.leInt
            byte[] fileMetadata=new byte[fileMetadataLength]
            buffer.get fileMetadata
            def event=new FileManifestEvent(
                    fileNameLength,
                    fileName,
                    nonCompressedFileSize,
                    unixFileTimeStamp,
                    compressedFileSize,
                    crc32Checksum,
                    bitFlags,
                    fileMetadataLength,
                    fileMetadata
            )
            list+=event
            files+=event
        }
        //read files
        files.each {event->
            byte[] fileBuffer=new byte[event.compressedFileSize]
            buffer.get fileBuffer
            list+=new RawFileEvent(fileBuffer,event)
        }
        //read signature
        int remain=buffer.remaining()
        int hashSize=remain-(4/*signature*/+4/*GBMB*/)
        if(hashSize!=16&hashSize!=20)
            throw new IllegalStateException()
        byte[] hash=new byte[hashSize]
        buffer.get hash
        int sigFlags=buffer.leInt
        list+=new SignatureEvent(
                hashSize,
                hash,
                sigFlags
        )
        //check GBMB
        char G=buffer.get()
        char B1=buffer.get()
        char M=buffer.get()
        char B2=buffer.get()
        if([G,B1,M,B2]=="GBMB".toCharArray().toList()){
            list+=new EofEvent()
        }
    }

    Iterator<PharParserEvent> iterator() {
        Collections.unmodifiableList(list).iterator()
    }

    void startReading(OnEventListener listener){
        listener.onEvent new BeginEvent()
        //find stub
        def channel=null
        int stubEnd=-1
        try {
            channel=new RandomAccessFile(f,"r")
            byte[] tmp=new byte[STUB_FINAL_C.length+20]
            for(long i=0;i<(channel.length()-STUB_FINAL_C.length-20);i++){
                channel.seek i
                channel.read tmp

                stubEnd=Bytes.indexOf(tmp,STUB_FINAL_A)
                if(stubEnd!=-1){
                    stubEnd=stubEnd+i+STUB_FINAL_A.length
                    break
                }
                stubEnd=Bytes.indexOf(tmp,STUB_FINAL_B)
                if(stubEnd!=-1){
                    stubEnd=stubEnd+i+STUB_FINAL_B.length
                }
                stubEnd=Bytes.indexOf(tmp,STUB_FINAL_C)
                if(stubEnd!=-1){
                    stubEnd=stubEnd+i+STUB_FINAL_C.length
                }
            }
            channel.seek stubEnd
            channel.read tmp,0,2
            def tmpStr=new String(tmp,0,2)
            if(tmpStr.startsWith("\r\n")){
                stubEnd+=2
            }else if(tmpStr.startsWith("\r")){
                stubEnd++
            }else if(tmpStr.startsWith("\n")){
                stubEnd++
            }
        } finally {
            if(channel!=null)channel.close()
        }


        def buffer=new LocalFileReader(f)
        if(stubEnd!=-1){
            byte[] readBuf=new byte[stubEnd]
            buffer.get readBuf
            listener.onEvent new StubEvent(readBuf)
        }
        //read manifest in LITTLE ENDIAN
        int manifestTotal=buffer.leInt
        int filesInsidePhar=buffer.leInt
        short pharApiVersion=buffer.short
        int globalFlags=buffer.leInt
        int pharAliasesLength=buffer.leInt
        byte[] pharAliases=new byte[pharAliasesLength]
        buffer.get pharAliases
        int pharMetadataLength=buffer.leInt
        byte[] pharMetadata=new byte[pharMetadataLength]
        buffer.get pharMetadata
        listener.onEvent new ManifestEvent(
                manifestTotal,
                filesInsidePhar,
                pharApiVersion,
                globalFlags,
                pharAliasesLength,
                pharAliases,
                pharMetadataLength,
                pharMetadata
        )
        //read file manifest
        List<FileManifestEvent> files=[]
        filesInsidePhar.times {
            int fileNameLength=buffer.leInt
            byte[] fileName=new byte[fileNameLength]
            buffer.get fileName
            int nonCompressedFileSize=buffer.leInt
            int unixFileTimeStamp=buffer.leInt
            int compressedFileSize=buffer.leInt
            int crc32Checksum=buffer.leInt
            int bitFlags=buffer.leInt
            int fileMetadataLength=buffer.leInt
            byte[] fileMetadata=new byte[fileMetadataLength]
            buffer.get fileMetadata
            def event=new FileManifestEvent(
                    fileNameLength,
                    fileName,
                    nonCompressedFileSize,
                    unixFileTimeStamp,
                    compressedFileSize,
                    crc32Checksum,
                    bitFlags,
                    fileMetadataLength,
                    fileMetadata
            )
            listener.onEvent event
            files+=event
        }
        //read files
        files.each {event->
            byte[] fileBuffer=new byte[event.compressedFileSize]
            buffer.get fileBuffer
            listener.onEvent new RawFileEvent(fileBuffer,event)
        }
        //read signature
        int remain=buffer.remaining()
        int hashSize=remain-(4/*signature*/+4/*GBMB*/)
        if(hashSize!=16&hashSize!=20) {
            listener.onEvent new ErrorEvent(new IllegalStateException("Wrong hash size: $hashSize"))
            return
        }
        byte[] hash=new byte[hashSize]
        buffer.get hash
        int sigFlags=buffer.leInt
        listener.onEvent new SignatureEvent(
                hashSize,
                hash,
                sigFlags
        )
        //check GBMB
        byte[] gbmbBuf=new byte[4]
        buffer.get(gbmbBuf)
        if(new String(gbmbBuf)=="GBMB"){
            listener.onEvent new EofEvent()
        }else{
            listener.onEvent new ErrorEvent(new IllegalStateException("GBMB not detected: hex: ${Hex.encodeHexString(gbmbBuf)}"))
        }
    }


    interface OnEventListener{
        void onEvent(PharParserEvent event)
    }
}
