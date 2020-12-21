
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;
import sun.security.util.BitArray;

import java.io.*;
import java.util.*;

public class Assignment2 {
    //set max memory size 1G
    static long MAXSIZE=1024L*1024L*1024L*2L;
    static int calString(String s){
        return s.length()*Character.BYTES;
    }
    static int tempdoc_id = 0;
    static int quantiBit=0;
    static int quantiFuc=0;
    static int useQuant=0;
    static HashMap<Integer,Integer> page=new HashMap<>();

/**
 *This method is used to create our temp inverted file, page table and lexicon
 */
    public static void buildtemp(String path)throws FileNotFoundException,IOException{
        //initial variable
        int term_id = 0;
        //This map is used lexicon
        //term as key，int[] as value. It contains term_id， how many document has this term
        Map<String, int[]> Lexicon = new HashMap<>();
        //doc_id as key, url as value
        Map<Integer, String> pageTable = new HashMap<>();
        Map<Integer,Integer> pageTableLength= new HashMap<>();
        //object[] contains term(string)，[doc_id,frequency](arraylist<int[]>)
        List<Object[]> invertedindex = new ArrayList<>();
        // Part 1 parser
        FileReader input = new FileReader(path);
        BufferedReader br = new BufferedReader(input, 10 * 1024 * 1024);
        String cur_line = "";
        // used to debug
        long num = 0;
        // current_memoory is used to store
        // the size of data that has been read
        long current_memory = 0;
        while ((cur_line = br.readLine()) != null) {
            // cur_data is used to store words in that document
            String cur_data = "";
            int docid = 0;
            if (cur_line.compareTo("<DOC>") == 0) {

                //obtain doc_id
                //the structure of document ID:<DOCNO>D{document ID}</DOCNO>
                String temp = br.readLine();
                int end = temp.indexOf("</DOCNO>");
                docid = Integer.parseInt(temp.substring(8, end));
                br.readLine();
                String url = br.readLine();
                //add this page to pageTable
                //pagetable.add(new Object[]{docid,url});
                pageTable.putIfAbsent(docid, url);
                //obtain Text
                cur_line = br.readLine();

                // each document start in <TEXT> and end in </TEXT>
                while (cur_line.compareTo("</TEXT>") != 0) {
                    current_memory += calString(cur_line);
                    cur_data = cur_data.concat(" " + cur_line);
                    cur_line = br.readLine();
                }

                //build inverted index, lexicon
                String regEx = "[`~!@#$%^&*()+=\\-|{}':;\",\\[\\].·′<>/?！￥…（）【】‘；：”“’。，、？°″√∠_-]";
                cur_data = cur_data.toLowerCase();
                //find the frequency of this term in this document
                cur_data = cur_data.replaceAll(regEx, " ");
                String[] result = cur_data.split(" ");
                pageTableLength.putIfAbsent(docid, result.length);
                //use hashmap to count
                HashMap<String, Integer> fre = new HashMap<>();
                for (String s : result) {
                    if (!fre.containsKey(s)) {
                        fre.put(s, 1);
                    } else {
                        fre.put(s, fre.get(s) + 1);
                    }
                }
                //add this document to our current lexicon
                for (String target : fre.keySet()) {
                    if (target.compareTo("") == 0||target.length()>=30||isNumeric(target))
                        continue;
                    //find the frequency of this target
                    int frequency = fre.get(target);
                    //if our current lexicon dont contain this term
                    //add it to lexicon and invertedindex
                    if (!Lexicon.containsKey(target)) {
                        //add new object[] to store this term.
                        //each object[] store a list which indicates
                        invertedindex.add(new Object[]{"", new ArrayList<int[]>()});
                        invertedindex.get(term_id)[0] = target;
                        ((ArrayList<int[]>) (invertedindex.get(term_id)[1])).add(new int[]{docid, fre.get(target)});
                        //term_id, frequency in doc
                        Lexicon.put(target, new int[]{term_id, 1});
                        term_id++;
                    } else {
                        //update Lexicon
                        Lexicon.get(target)[1]++;
                        // add new post
                        ((ArrayList<int[]>) invertedindex.get(Lexicon.get(target)[0])[1]).add(new int[]{docid, frequency});
                    }
                }

                System.out.print("Fraction: ");
                System.out.println(num);
                num++;
                if (current_memory >= MAXSIZE) {
                    System.out.println("start output temp");
                    String temp_file_path = Integer.toString(tempdoc_id).concat(".bin");
                    String temp_map_path = Integer.toString(tempdoc_id).concat("page.bin");
                    DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp_file_path), 1024 * 1024 * 50));
                    invertedindex.sort((o1, o2) -> {
                        return ((String) o1[0]).compareTo(((String) o2[0]));
                    });

                    //////*****************************
                    //struct：(term ,frequency, many dataset[docid, frequency in that file] )
                    // the number of dataset is frequency
                    //******************************

                    for (int i = 0; i < invertedindex.size(); i++) {
                        //write term string
                        String cur_term = (String) invertedindex.get(i)[0];
                        output.writeUTF(cur_term);
                        //write how many documents related to this term
                        int num_doc = Lexicon.get(cur_term)[1];
                        output.writeInt(num_doc);
                        //release memory
                        //write inverted Index
                        List<int[]> cur_index = (List<int[]>) invertedindex.get(i)[1];
                        cur_index.sort((o1, o2) -> o1[0] - o2[0]);
                        for (int[] cur_value : cur_index) {
                            output.writeInt(cur_value[0]);
                            output.writeInt(cur_value[1]);
                        }
                    }
                    //release memory
                    invertedindex = new ArrayList<>();
                    current_memory = 0;
                    Lexicon = new HashMap<>();
                    output.flush();
                    output.close();
                    DataOutputStream output1 = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp_map_path), 1024 * 1024 * 50));
                    for(Integer Docid: pageTable.keySet()) {
                        output1.writeInt(Docid);
                        output1.writeUTF(pageTable.get(Docid));
                        output1.writeInt(pageTableLength.get(Docid));
                    }
                    output1.flush();
                    output1.close();
                    pageTable = new HashMap<>();
                    pageTableLength=new HashMap<>();
                    System.out.println("finished tempfile");
                    tempdoc_id++;
                    //reinitial term id
                    term_id = 0;
                    System.gc();

                }
            } else {
                continue;
            }
        }
        if (!invertedindex.isEmpty()) {
            System.out.println("start output temp");
            String temp_file_path = Integer.toString(tempdoc_id).concat(".bin");
            String temp_map_path = Integer.toString(tempdoc_id).concat("page.bin");
            DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp_file_path), 1024 * 1024 * 50));
            invertedindex.sort((o1, o2) -> {
                return ((String) o1[0]).compareTo(((String) o2[0]));
            });

            //////*****************************
            //result form：(tem,frequency_doc，docid，frequency)
            //
            //******************************

            for (int i = 0; i < invertedindex.size(); i++) {
                //write term string
                String cur_term = (String) invertedindex.get(i)[0];
                output.writeUTF(cur_term);
                //write how many documents related to this term
                int num_doc = Lexicon.get(cur_term)[1];
                output.writeInt(num_doc);
                //release memory
                //write inverted Index
                List<int[]> cur_index = (List<int[]>) invertedindex.get(i)[1];
                cur_index.sort((o1, o2) -> o1[0] - o2[0]);
                for (int[] cur_value : cur_index) {
                    output.writeInt(cur_value[0]);
                    output.writeInt(cur_value[1]);
                }
                //release memory
            }
            Lexicon = new HashMap<>();
            invertedindex = new ArrayList<>();
            current_memory = 0;
            output.close();
            DataOutputStream output1 = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp_map_path), 1024 * 1024 * 50));
            for(Integer Docid: pageTable.keySet()) {
                output1.writeInt(Docid);
                output1.writeUTF(pageTable.get(Docid));
                output1.writeInt(pageTableLength.get(Docid));
            }
            output1.close();
            pageTable = new HashMap<>();
            pageTableLength=new HashMap<>();
            System.out.println("finished tempfile");
            tempdoc_id++;
            //reinitial term id
            term_id = 0;
            System.gc();
        }
    }

    /**
     * this function is used to merge the temp file we output above
     * @param numoffile is the number of tempfile we output
     * @throws IOException
     */
    public static void merge(int numoffile) throws IOException, InterruptedException {
        //output stream
        DataOutputStream invlist = new DataOutputStream(new BufferedOutputStream(new FileOutputStream("InvertedList.bin")));
        DataOutputStream lexicon = new DataOutputStream(new BufferedOutputStream(new FileOutputStream("Lexicon.bin")));
        DataOutputStream pagetable = new DataOutputStream(new BufferedOutputStream(new FileOutputStream("pagetable.bin")));

        //relation is used to connect the invertedindex with the tempfile where we get that invertedindex
        ArrayList<Object[]> relation = new ArrayList<>();
        //store input stream
        DataInputStream[] pagefile = new DataInputStream[numoffile+1];
        // we simply want to finish combination in one merge
        // so we initialize num of file queues.
        for (int i = 0; i < numoffile+1; i++) {
            String filename = "".concat(i + ".bin");
            String filename1 = "".concat(i + "page.bin");
            DataInputStream is = new DataInputStream(new BufferedInputStream(new FileInputStream(filename)));
            DataInputStream is1 = new DataInputStream(new BufferedInputStream(new FileInputStream(filename1)));
            Object[] temp = new Object[2];
            temp[0] = is;
            temp[1] = readdata(is);
            pagefile[i] = is1;
            relation.add(temp);
        }
        Comparator<Object[]> myComparator = new Comparator<Object[]>() {
            @Override
            public int compare(Object[] o1, Object[] o2) {
                Object[] i1 = (Object[]) o1[1];
                Object[] i2 = (Object[]) o2[1];
                return ((String) i1[0]).compareTo(((String) i2[0]));
            }
        };
        long pointer = 0;
        //merge temp file
        while (!relation.isEmpty()) {
            // for each queue, we extract the invertedindex ,whose term has lowest ASCII value, as target.
            // it is just same as merge sort
            Collections.sort(relation, myComparator);
            String cur_term = (String) ((Object[]) ((Object[]) relation.get(0))[1])[0];
            int numDoc = 0;
            ArrayList<int[]> InvertedList = new ArrayList<>();
            // scan all result to check if there are is other invertedindex whose term is same as target.
            // if there is combine them otherwise just go next.
            for (int i = 0; i < relation.size(); i++) {

                // the result form readdata: Object[]{term, numDoc, list}
                Object[] temp = (Object[]) relation.get(i)[1];
                String cur_temp_term = (String) temp[0];
                //check term
                if (cur_temp_term.compareTo(cur_term) == 0) {
                    // look tricky but it is just simply combine result.
                    InvertedList.addAll((List) (temp[2]));
                    // update the number of document that contain this term which is used to
                    numDoc += (int) (temp[1]);
                    relation.get(i)[1] = readdata((DataInputStream) relation.get(i)[0]);
                    if (relation.get(i)[1] == null) {
                        ((DataInputStream) relation.get(i)[0]).close();
                        relation.remove(relation.get(i));
                    }
                }
            }
            System.out.println(cur_term);
            lexicon.writeUTF(cur_term);
            lexicon.writeLong(pointer);
            lexicon.writeInt(numDoc);
            // sort invertedindex
            /*
            InvertedList.sort((o1, o2) -> o1[0] - o2[0]);
            for (int i = 0; i < InvertedList.size(); i++) {
                invlist.writeInt(InvertedList.get(i)[0]);
                invlist.writeInt(InvertedList.get(i)[1]);
            }
            */
            if(cur_term.compareTo("dog")==0){
                int debug=1;
            }
            pointer=output(InvertedList,invlist,pointer,numDoc);
            lexicon.flush();
            invlist.flush();
        }
        //it is used to sort page table
        Queue<Object[]> check = new PriorityQueue<>(new Comparator<Object[]>() {
            @Override
            public int compare(Object[] o1, Object[] o2) {
                return (int) o1[0] - (int) o2[0];
            }
        });
        for (int i = 0; i < pagefile.length; i++) {
            if (pagefile[i] == null)
                continue;
            int docid = pagefile[i].readInt();
            String url = pagefile[i].readUTF();
            int pagesize=pagefile[i].readInt();
            check.add(new Object[]{docid, url, pagesize, pagefile[i]});
        }
        int flag = pagefile.length;
        while (flag != 0) {
            Object[] result = check.poll();
            pagetable.writeInt((int) result[0]);
            pagetable.writeUTF((String) result[1]);
            pagetable.writeInt((int)result[2]);
            page.put((int) result[0],(int)result[2]);
            DataInputStream cur_file = (DataInputStream) result[3];
            try {
                check.add(new Object[]{cur_file.readInt(), cur_file.readUTF(),cur_file.readInt(), cur_file});
            } catch (EOFException e) {
                cur_file.close();
                flag--;
            }
        }
        pagetable.flush();
    }
    public static long output(List<int[]>invertedindex,DataOutputStream out,long pointer,int numDoc) throws IOException, InterruptedException {
        invertedindex.sort(new Comparator<int[]>() {
            @Override
            public int compare(int[] o1, int[] o2) {
                return o1[0]-o2[0];
            }
        });
        for (int i=0;i<invertedindex.size();i++){
            if(invertedindex.get(i)[0]==2309588){
                int k=123123;
            }
        }
        int BlockSize=4096;
        int blockNum=invertedindex.size()%BlockSize==0?invertedindex.size()/BlockSize:invertedindex.size()/BlockSize+1;
        LinkedList<Byte> blockNumCompre=VBEncode(blockNum);
        for(int j=0;j<blockNumCompre.size();j++){
            out.write(blockNumCompre.get(j));
        }
        pointer+=blockNumCompre.size();
        int index=0;
        for(int i=0;i<blockNum;i++){
            int previou_docid=0;
            ArrayList<LinkedList<Byte>> temp_docid=new ArrayList<>();
            ArrayList<LinkedList<Byte>> temp_fre=new ArrayList<>();
            int lastDocid=0;
            for(int j=0;j<BlockSize;j++) {
                int cur_index=BlockSize*index+j;
                if (cur_index>=invertedindex.size())
                    continue;
                int cur_docid = invertedindex.get(cur_index)[0] - previou_docid;
                //for debug
                if (lastDocid>invertedindex.get(cur_index)[0]){
                    System.out.println("Error");
                    Thread.sleep(5000000);
                }
                lastDocid=invertedindex.get(cur_index)[0];
                if (lastDocid==266807) {
                    int kkkk = 123;
                }
                int frequency = invertedindex.get(cur_index)[1];
                previou_docid = invertedindex.get(cur_index)[0];
                temp_docid.add(VBEncode(cur_docid));

                //*********************************
                //Change frequency to quantized score
                //
                //
                //************************************
                if (useQuant==0) {
                    temp_fre.add(VBEncode(frequency));
                }
                else{
                    double Score=calScore(previou_docid,frequency,numDoc);
                }
            }
            index++;
            int cur_blocksize=0;
            for(int j=0;j<temp_docid.size();j++){
                cur_blocksize=cur_blocksize+temp_docid.get(j).size()+temp_fre.get(j).size();
            }
            if(cur_blocksize<0) {
                System.out.println("Error");
                Thread.sleep(500000000);
            }

            //last docID of this list
            LinkedList<Byte> lastDocidcom=VBEncode(lastDocid);
            for (int j = 0; j < lastDocidcom.size(); j++) {
                out.write(lastDocidcom.get(j));
            }
            //block size
            LinkedList<Byte> cur_blocksizecom=VBEncode(cur_blocksize);
            for (int j = 0; j < cur_blocksizecom.size(); j++) {
                out.write(cur_blocksizecom.get(j));
            }
            pointer=pointer+cur_blocksizecom.size()+lastDocidcom.size()+cur_blocksize;
            for(int m=0;m<temp_docid.size();m++){
                for(int n=0;n<temp_docid.get(m).size();n++){
                    out.write(temp_docid.get(m).get(n));
                }
                for(int n=0;n<temp_fre.get(m).size();n++){
                    out.write(temp_fre.get(m).get(n));
                }
            }
        }

        /*
        for (int i=0;i<invertedindex.size();i++){
            if(i%4096==0){
                previou_docid=0;
            }
            int cur_docid=invertedindex.get(i)[0]-previou_docid;
            int frequency=invertedindex.get(i)[1];
            previou_docid=invertedindex.get(i)[0];
            LinkedList<Byte> tempdocid=VBEncode(cur_docid);
            for(int j=0;j<tempdocid.size();j++){
                out.write(tempdocid.get(j));
            }
            LinkedList<Byte> fre=VBEncode(frequency);
            for(int j=0;j<fre.size();j++){
                out.write(fre.get(j));
            }
            pointer=pointer+tempdocid.size()+fre.size();
        }
         */
        return pointer;


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
        Collections.reverse(byteStream);
        return byteStream;                  //返回byteStream
    }

    /**
     * this function is used to read a complete invertedindex for one term in one specific file
     * @param is the inputStream of current file
     * @return the data we get from this file Object[]{term, numDoc, list}
     */
    public static Object[]readdata(DataInputStream is) {
        try {
            String term = is.readUTF();
            int numDoc = is.readInt();
            List<int[]> list = new ArrayList<>();
            for (int i = 0; i < numDoc; i++) {
                int[] temp = new int[]{is.readInt(), is.readInt()};
                list.add(temp);
            }
            Object[] result = new Object[]{term, numDoc, list};
            return result;
        }catch (EOFException e){
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean isNumeric(String strNum) {
        if(strNum.matches("-?\\d+(\\.\\d+)?")&&strNum.length()>15){
            return true;
        }
        return false;
    }


    public static double calScore(int docid,int frequency, int numDoc){
        int cur_doc_length=page.get(docid);
        int totalnumofdoc=3213835;
        long totollength = 4498523269L;
        double temp=Math.log((totalnumofdoc-numDoc+0.5)/(numDoc+0.5));
        double K=1.2*((1-0.75)+0.75*cur_doc_length/(totollength/totalnumofdoc));
        temp=temp*(1.2+1)*frequency/(K+frequency);
        return temp;
    }





    public static LinkedList<Byte> quantization(float n){
        BitSet a=new BitSet();
        return null;
    }




    /**
     * this is our main function
     * @param args
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException, InterruptedException {
        System.out.println("default memory is set to 2GB");
        System.out.println("if you want change, pls change the MAXIMUM value in code");
        System.out.println("Please Enter the path of your trec file:");
        Scanner myScanner=new Scanner(System.in);
        String path=myScanner.next();
        //buildtemp(path);
        tempdoc_id=21;
        System.out.println("Please Enter if you want to use quantization(Enter 0: don't use, 1:use):");
        useQuant=myScanner.nextInt();
        if (useQuant==1){
            System.out.println("how many bits you want to quantize BM25:");
            quantiBit=myScanner.nextInt();
            System.out.println("pls enter which quantization function you want to use:");
            quantiFuc=myScanner.nextInt();
        }

        merge(tempdoc_id-1);



        /*
        for (int i=0;i<tempdoc_id;i++){
            String tempname=Integer.toString(i).concat(".bin");
            String tempname1=Integer.toString(i).concat("page.bin");
            File del_temp=new File(tempname);
            File del_tab=new File(tempname1);
            del_temp.delete();
            del_tab.delete();
        }

         */
    }
}
