import java.io.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

                /*
                DataInputStream is = new DataInputStream(new BufferedInputStream(new FileInputStream("D:\\Assignment2\\InvertedList.bin")));
                int k = 1;
                is.skip(pointer);
                for (int i = 0; i < numDoc; i++) {
                    int a = readInt(is);
                    int b = readInt(is);
                    int k1 = 123123;
                }
                 */

public class ReadInvertedList {
    public static void main(String[] args) throws IOException {
        HashMap<String,int[]> Lexicon=new HashMap<>(150000000);
        readLexicon(Lexicon);
        int k=0;
    }

    public static void readLexicon(HashMap Lexicon)throws IOException {
        DataInputStream is1 = new DataInputStream(new BufferedInputStream(new FileInputStream("D:\\\\Assignment2\\\\Lexicon.bin")));
        int i=0;
        String term;
        try{
            while((term = is1.readUTF())!=null) {
                //System.out.println(i);
                if(term.length()>=30)
                    System.out.println(term);
                i++;
                int pointer = (int)is1.readLong();
                int numDoc = is1.readInt();
                Lexicon.put(term,new Object[]{numDoc,pointer});
        }
        }catch (EOFException e){ }
    }

    public static void output() throws IOException {
        int cur_docid=1243;
        int frequency=3;
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream("xxx123.bin")));
        LinkedList<Byte> tempdocid=VBEncode(cur_docid);
        for(int j=0;j<tempdocid.size();j++){
            out.write(tempdocid.get(j));
        }
        LinkedList<Byte> fre=VBEncode(frequency);
        for(int j=0;j<fre.size();j++){
            out.write(fre.get(j));
        }
        System.out.println(tempdocid.size()+fre.size());
        out.flush();
        out.close();
    }

    public static LinkedList<Byte> VBEncode(int n){
        //store result
        LinkedList<Byte> byteStream=new LinkedList<>();
        //seperate integer. each 128 as one group
        LinkedList<Integer> bytes=new LinkedList<>();
        while(true){
            bytes.add((n%128));
            if(n<128)
                break;
            n/=128;
        }
        for (int i=0;i<bytes.size();i++){
            //convert to byte
            if(i!=0) {
                bytes.set(i, bytes.get(i) + 128);
            }
            Byte result=(byte)(bytes.get(i)>>0 & 0xff);
            byteStream.add(result);
        }
        return byteStream;                  //返回byteStream
    }

    public static int readInt(DataInputStream is) throws IOException {
        int temp=is.readByte();
        byte readVal;
        int counter=1;
        if(temp<0) {
            while ((readVal = is.readByte()) < 0) {
                readVal = (byte) (readVal + 128);
                temp += readVal << counter * 7;
                counter++;
            }
            return temp;
        }
        return temp;
    }




}
