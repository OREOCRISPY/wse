

import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;
import sun.reflect.generics.tree.Tree;

import java.io.*;
import java.lang.reflect.Array;
import java.util.*;

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
    public static long totollength=0;
    public static int totalnumofdoc=0;
    //compressed Block for conjunctive query
    public static ArrayList<int[]>[]temp;


    public static int[]check;
    public static int[]frequency;
    public static int[] initial;
    public static int[]initialCopy;
    // URL BM25 Snippet
    public static PriorityQueue<Object[]>Queryresult;
    public static void main(String[] args) throws IOException, InterruptedException {
        HashMap<String,Object[]> Lexicon=new HashMap<>(150000000);
        HashMap<Integer,Object[]>page=new HashMap<>(1000000);
        //String[]Lexicon=new String[25981946];
        /*
        TreeMap<String,Object[]> Lexicon=new TreeMap<String,Object[]>(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        });

         */

        readLexicon(Lexicon);
        readPage(page);
        String query;
        Scanner myScanner=new Scanner(System.in);
        while(true) {
            System.out.println("pls input");
            query = myScanner.nextLine();
            query.toLowerCase();
            String[] terms=query.split(" ");
            String result=QueryProcess_Disjunctive(Lexicon,page,terms);
            if (result.compareTo("No page Match")==0){
                System.out.println(result);
                continue;
            }
            System.out.println("search result:");
            for (int i=0;i<10;i++) {
                Object[] temp=Queryresult.poll();
                System.out.println("Result"+i+":");
                System.out.println(temp[0]);
                System.out.println(temp[1]);
                System.out.println(temp[2]);
            }
        }
    }

    public static String QueryProcess(HashMap<String,Object[]> Lexicon,HashMap<Integer,Object[]>page, String[] terms) throws IOException, InterruptedException {
        Queryresult=new PriorityQueue<>(new Comparator<Object[]>() {
            @Override
            public int compare(Object[] o1, Object[] o2) {
                double result=(double)o1[1]-(double)o2[1];
                if (result==0){
                    return 0;
                }
                if (result>0){
                    return 1;
                }
                else {
                    return -1;
                }
            }
        });

        DataInputStream[] lp=openList(terms.length);
        Object[][] lexicon=new Object[terms.length][2];
        temp=new ArrayList[terms.length];
        check=new int[terms.length];
        // store the current frequency of target post
        frequency=new int[terms.length];
        initial=new int[terms.length];
        initialCopy=new int[terms.length];
        int[] Blocknum=new int[terms.length];
        for(int i=0;i< terms.length;i++){
            if (!Lexicon.containsKey(terms[i])){
                return "No page Match";
            }
        }
        Arrays.sort(terms, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return (int)Lexicon.get(o1)[0]-(int)Lexicon.get(o2)[0];
            }
        });
        for(int i=0;i< terms.length;i++){
            //[numDoc,pointer]
            lexicon[i] = Lexicon.get(terms[i]);
        }
        for(int i=0;i<lexicon.length;i++){
            temp[i]=new ArrayList<>();
            lp[i].skip((long)lexicon[i][1]);
            initial[i]=lp[i].readByte();
            Blocknum[i]=readInt(lp[i],i);
            initialCopy[i]=initial[i];
            lp[i].mark(4096*20*Blocknum[i]);
        }
        //1:
        int MaxDocid=findMax(lp[0],Blocknum[0]);
        int did=0;
        int d=0;
        while(did<=MaxDocid){
            did=nextGEQ(lp[0],did,Blocknum[0],0);
            for(int i=1;(i< terms.length)&&((d=nextGEQ(lp[i],did,Blocknum[i],i))==did);i++);
            if(d>did)
                did=d;
            else {
                double BM25=0;
                for(int i=0;i<terms.length;i++){
                    //System.out.println(did);
                    //System.out.println(frequency[i]);
                    //System.out.println(page.get(did)[0]);
                    BM25=BM25cal(frequency[i],(int)page.get(did)[1],(int)lexicon[i][0]);
                    String Snippet="";
                    Object[] curr_result=new Object[]{page.get(did)[0],BM25,Snippet};
                    Queryresult.add(curr_result);
                    if(Queryresult.size()>10){
                        Queryresult.poll();
                    }
                }
                did++;
            }
        }
        return "finished";
    }


    public static String QueryProcess_Disjunctive(HashMap<String,Object[]> Lexicon,HashMap<Integer,Object[]>page, String[] terms) throws IOException, InterruptedException {
        Queryresult=new PriorityQueue<>(new Comparator<Object[]>() {
            @Override
            public int compare(Object[] o1, Object[] o2) {
                double result=(double)o1[1]-(double)o2[1];
                if (result==0){
                    return 0;
                }
                if (result>0){
                    return 1;
                }
                else {
                    return -1;
                }
            }
        });

        DataInputStream[] lp=openList(terms.length);
        Object[][] lexicon=new Object[terms.length][2];
        temp=new ArrayList[terms.length];
        //used to check if current block was uncompressed before
        check=new int[terms.length];
        // store the current frequency of target post
        frequency=new int[terms.length];
        // store location in read stream
        initial=new int[terms.length];
        // used to relocate location in read stream
        initialCopy=new int[terms.length];
        // used to store how many blocks in this inverted list
        int[] Blocknum=new int[terms.length];
        int flag=0;
        for(int i=0;i< terms.length;i++){
            if (Lexicon.containsKey(terms[i])){
                flag++;
            }
        }
        if(flag==0){
            return "No match";
        }
        // index cur_docid
        PriorityQueue<int[]> disjunctive=new PriorityQueue<>(new Comparator<int[]>() {
            @Override
            public int compare(int[] o1, int[] o2) {
                return o1[1]-o2[1];
            }
        });
        Arrays.sort(terms, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return (int)Lexicon.get(o1)[0]-(int)Lexicon.get(o2)[0];
            }
        });
        for(int i=0;i< terms.length;i++){
            //[numDoc,pointer]
            lexicon[i] = Lexicon.get(terms[i]);
        }
        for(int i=0;i<lexicon.length;i++){
            temp[i]=new ArrayList<>();
            lp[i].skip((long)lexicon[i][1]);
            initial[i]=lp[i].readByte();
            Blocknum[i]=readInt(lp[i],i);
            initialCopy[i]=initial[i];
            lp[i].mark(4096*10*Blocknum[i]);
        }
        //1:
        int MaxDocid[]=new int[terms.length];
        for (int i=0;i<terms.length;i++) {
            MaxDocid[i]=findMax(lp[i], Blocknum[i]);
            int first=nextGEQ(lp[i],0,Blocknum[i],i);
            disjunctive.add(new int[]{i,first});
        }
        while(disjunctive.size()!=0){
            ArrayList<int[]>disjunctive_re=new ArrayList<>();
            int cur_index=0;
            int cur_docid=0;
            double BM25=0;
            int[] cur_top=disjunctive.peek();
            while(disjunctive_re.size()==0||cur_top[1]==cur_docid) {
                int[] cur_list = disjunctive.poll();
                cur_index = cur_list[0];
                cur_docid = cur_list[1];
                Object[] aa=page.get(cur_docid);
                //System.out.println(page.get(cur_docid)[1]);
                BM25+=BM25cal(frequency[cur_index],(int)page.get(cur_docid)[1],(int)lexicon[cur_index][0]);
                if (cur_docid<MaxDocid[cur_index]) {
                    disjunctive_re.add(new int[]{cur_index, cur_docid});
                }
                if (disjunctive.size()==0){
                    break;
                }
                cur_top=disjunctive.peek();
            }
            String Snippet="";
            Object[] curr_result=new Object[]{page.get(cur_docid)[0],BM25,Snippet};
            Queryresult.add(curr_result);
            if(Queryresult.size()>10){
                Queryresult.poll();
            }
            for (int i=0;i<disjunctive_re.size();i++) {
                int [] previous_list=disjunctive_re.get(i);
                int did = nextGEQ(lp[previous_list[0]], previous_list[1]+1, Blocknum[previous_list[0]], previous_list[0]);
                if (did<=MaxDocid[previous_list[0]]){
                    disjunctive.add(new int[]{previous_list[0],did});
                }
            }
        }
        return "finished";
    }






    public static double BM25cal(int frequency,int cur_doc_length, int numDoc){
        double temp=Math.log((totalnumofdoc-numDoc+0.5)/(numDoc+0.5));
        double K=1.2*((1-0.75)+0.75*cur_doc_length/(totollength/totalnumofdoc));
        temp=temp*(1.2+1)*frequency/(K+frequency);
        return temp;
    }





    public static int findMax(DataInputStream lp,int Blocknumber) throws IOException, InterruptedException {
        int cur_block=0;
        int LastDocId=0;
        int BlockSize=0;
        for (cur_block=0;cur_block<Blocknumber;cur_block++){
            LastDocId=readInt(lp,0);
            BlockSize=readInt(lp,0);
            lp.skipBytes(BlockSize-1);
            initial[0]=lp.readByte();
        }
        lp.reset();
        initial[0]=initialCopy[0];
        return LastDocId;
    }

    public static int nextGEQ(DataInputStream in,int docid, int Blocknum, int index) throws IOException, InterruptedException {
        if(docid==266763){
            int kkkkk=123;
        }
        int LastDocId=readInt(in,index);
        int BlockSize=readInt(in,index);
        int cur_block=0;
        while (docid>LastDocId){
            in.skipBytes(BlockSize-1);
            initial[index]=in.readByte();
            cur_block++;
            if(cur_block==Blocknum){
                in.reset();
                initial[index]=initialCopy[index];
                return LastDocId;
            }
            LastDocId=readInt(in,index);
            BlockSize=readInt(in,index);
        }
        int cur_doc=0;
        int cur_fre=0;

        if(check[index]!=cur_block||temp[index].size()==0) {
            temp[index]=new ArrayList<>();
            do {
                cur_doc += readInt(in,index);
                cur_fre = readInt(in,index);
                if (cur_block==1&&temp[index].size()==4090){
                    int asdwa=123;
                }
                if(cur_doc<=LastDocId) {
                    int[] result = new int[]{cur_doc, cur_fre};
                    temp[index].add(result);
                }
            } while (cur_doc <= LastDocId);
            check[index]=cur_block;
        }
        int ind=0;
        int curr=temp[index].get(temp[index].size()-1)[0];
        while(ind<temp[index].size()&&temp[index].get(ind)[0]<docid){
            ind++;
            if(ind==temp[index].size()-1){
                int kkkk=temp[index].get(ind)[0];
                int kk=123;
            }
        }
        in.reset();
        initial[index]=initialCopy[index];
        frequency[index]=temp[index].get(ind)[1];

        return temp[index].get(ind)[0];
    }

    public static DataInputStream[] openList(int length) throws FileNotFoundException {
        DataInputStream[] lp=new DataInputStream[length];
        for(int i=0;i<length;i++){
            lp[i]=new DataInputStream(new BufferedInputStream(new FileInputStream("D:\\Assignment2\\InvertedList.bin")));
        }
        return lp;
    }


    public static void readPage(HashMap page) throws IOException {
        DataInputStream is1 = new DataInputStream(new BufferedInputStream(new FileInputStream("D:\\\\Assignment2\\\\pagetable.bin")));
        int i=0;
        int docid;
        String url;
        int length;
        while (true){
            try{
                docid=is1.readInt();
                url=is1.readUTF();
                length=is1.readInt();
                page.put(docid,new Object[]{url,length});
                totalnumofdoc++;
                totollength=totollength+length;
            }catch (EOFException e){
                break;
            }
        }
        is1.close();
    }

    public static void readLexicon(HashMap<String, Object[]> Lexicon)throws IOException {
        DataInputStream is1 = new DataInputStream(new BufferedInputStream(new FileInputStream("D:\\\\Assignment2\\\\Lexicon.bin")));
        int i=0;
        String term;
        long pointer;
        int numDoc;
        try{
            while(true) {
                //System.out.println(i);
                //System.out.println(term);
                //Lexicon[i]=term.toCharArray();
                term = is1.readUTF();
                i++;
                pointer = is1.readLong();
                numDoc = is1.readInt();
                Lexicon.put(term,new Object[]{numDoc,pointer});
        }
        }catch (EOFException e){ System.out.println(i);is1.close();}
    }




    public static int readInt(DataInputStream is,int index) throws IOException, InterruptedException {
        int result=initial[index];
        byte readVal;
        int counter=1;
        try {
            while ((readVal = is.readByte()) < 0) {
                readVal = (byte) (readVal + 128);
                result += readVal << counter * 7;
                counter++;
            }
        }catch (EOFException e){
            return result;
        }
        initial[index]=readVal;
        return result;
    }


}
