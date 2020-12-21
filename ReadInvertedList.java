

import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;
import sun.reflect.generics.tree.Tree;

import java.io.*;
import java.lang.reflect.Array;
import java.sql.*;
import java.util.*;


public class ReadInvertedList {

    // store the total length of all document
    // it is used to calculate average length
    public static long totollength=0;

    //it is used to store the number of documents in this collection
    public static int totalnumofdoc=0;
    //it is used to store the uncompressed inverted list
    public static ArrayList<int[]>[]temp;
    //it is used to store current index in current uncompressed inverted list
    public static int temp_index[];

    //it is used to check if this block was uncompressed before
    public static int[]check;
    // store the current frequency of target post
    public static int[]frequency;
    //it is the first byte in input stream
    //it is used to help VB decoder work
    public static int[] initial;
    //it is the copy of original initial
    //it is used to reset input stream help VB decoder work
    public static int[]initialCopy;
    // used to store query result.
    // result structure {URL BM25 Docid}
    public static PriorityQueue<Object[]>Queryresult;
    public static void main(String[] args) throws IOException, InterruptedException, SQLException, ClassNotFoundException {
        //use hashmap to store pagetable
        HashMap<Integer,Object[]>page=new HashMap<>(1000000);
        //use treemap to store lexicon
        TreeMap<String,Object[]> Lexicon=new TreeMap<String,Object[]>(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        });

        readLexicon(Lexicon);
        readPage(page);
        String query;
        Scanner myScanner=new Scanner(System.in);
        while(true) {
            System.out.println("please Enter:\n 0 to choose Disjunctive query\n 1 to choose Conjunctive query");
            int choose =myScanner.nextInt();
            myScanner.nextLine();
            System.out.println("please enter your query:");
            query = myScanner.nextLine();
            query.toLowerCase();
            // get query terms
            String[] terms=query.split(" ");
            String result;
            if (choose==0)
                result=QueryProcess_Disjunctive(Lexicon,page,terms);
            else
                result=QueryProcess(Lexicon,page,terms);
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
                System.out.println(snippetGenerator((int)temp[2],terms));
            }
        }
    }

    /**
     * This function is used to process Conjunctive query
     * @param Lexicon
     * @param page
     * @param terms
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public static String QueryProcess(TreeMap<String,Object[]> Lexicon,HashMap<Integer,Object[]>page, String[] terms) throws IOException, InterruptedException {
        //initial priorityQueue
        //Query result is used to store the result returned by our program
        //but only keep ten results with highest BM25
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
        //temp_index is used to set the current location in one block
        //so that program dont need to rescan complete blocks for each docid
        temp_index=new int[terms.length];
        //for each term, we create separated inputstream
        //so that we dont need to care about input pointer
        DataInputStream[] lp=openList(terms.length);
        // for each term, we need to store the result get from Lexicon
        Object[][] lexicon=new Object[terms.length][2];
        //it is used to store current uncompressed block
        temp=new ArrayList[terms.length];
        //it is used to check if we need to uncompress new block
        check=new int[terms.length];
        // store the current frequency of target post
        frequency=new int[terms.length];
        //store the current byte of block
        //used to implement VB decoder
        initial=new int[terms.length];
        //store the initial byte of block
        //used to implement VB decoder
        initialCopy=new int[terms.length];
        //Store how many blocks are in posts
        int[] Blocknum=new int[terms.length];
        // check if there are some documents in this collection
        //satisfy conjunctive query
        for(int i=0;i< terms.length;i++){
            if (!Lexicon.containsKey(terms[i])){
                return "No page Match";
            }
        }
        //sort our terms according to the their doc frequency
        Arrays.sort(terms, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return (int)Lexicon.get(o1)[0]-(int)Lexicon.get(o2)[0];
            }
        });
        for(int i=0;i< terms.length;i++){
            //[numDoc,pointer]
            //retrieve lexicon
            lexicon[i] = Lexicon.get(terms[i]);
        }
        for(int i=0;i<lexicon.length;i++){
            temp[i]=new ArrayList<>();
            // locate posts for this term
            lp[i].skip((long)lexicon[i][1]);
            //initial[i]=lp[i].readByte();
            Blocknum[i]=NewreadInt(lp[i],i);
            //initialCopy[i]=initial[i];
            lp[i].mark(4096*20*Blocknum[i]);
        }
        // find the Max docid in the shortest list.
        int MaxDocid=findMax(lp[0],Blocknum[0],0);
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
                    BM25=BM25cal(frequency[i],(int)page.get(did)[1],(int)lexicon[i][0]);
                    String Snippet="";
                    Object[] curr_result=new Object[]{page.get(did)[0],BM25,did};
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


    public static String QueryProcess_Disjunctive(TreeMap<String,Object[]> Lexicon,HashMap<Integer,Object[]>page, String[] terms) throws IOException, InterruptedException {
        long curtime0=System.currentTimeMillis();
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
        temp_index=new int[terms.length];
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
        //this heap is used to store the lowest docid from three inverted list
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
            //initial[i]=lp[i].readByte();
            Blocknum[i]=NewreadInt(lp[i],i);
            //initialCopy[i]=initial[i];
            lp[i].mark(4096*10*Blocknum[i]);
        }
        //find the maxDocid for earch terms' posts
        int MaxDocid[]=new int[terms.length];
        //generate the first docid in each term's posts and store them in disjunctive heap
        for (int i=0;i<terms.length;i++) {
            MaxDocid[i]=findMax(lp[i], Blocknum[i],i);
            int first=nextGEQ(lp[i],0,Blocknum[i],i);
            disjunctive.add(new int[]{i,first});
        }
        //used to store the docid generated by nextGEQ
        int did=-1;
        //check if we have finished all posts
        while(disjunctive.size()!=0){
            //it is used to store the inverted list we retrieved from heap
            ArrayList<int[]>disjunctive_re=new ArrayList<>();
            int cur_index=0;
            int cur_docid=0;
            double BM25=0;
            int[] cur_top=disjunctive.peek();
            //check if one document contains more than one term
            while(disjunctive_re.size()==0||cur_top[1]==cur_docid) {
                //pull one from our heap
                int[] cur_list = disjunctive.poll();
                cur_index = cur_list[0];
                cur_docid = cur_list[1];
                //System.out.println(page.get(cur_docid)[1]);
                //calculate its BM25
                double cur_term_BM25=BM25cal(frequency[cur_index],(int)page.get(cur_docid)[1],(int)lexicon[cur_index][0]);
                BM25+=cur_term_BM25;
                if (cur_docid<MaxDocid[cur_index]) {
                    //it is used to omit useless term
                    //when BM25 is less than 0, it means this term is a common word like "is ,and ,or"
                    //it means nothing in our query
                    if (cur_term_BM25<0){
                        //however, we should also take care when our query only contains two words
                        //then this common term can be useful
                        if ((disjunctive.size()+disjunctive_re.size())<2){
                            disjunctive_re.add(new int[]{cur_index, cur_docid});
                        }
                    }
                    else{
                        disjunctive_re.add(new int[]{cur_index, cur_docid});
                    };
                }
                if (disjunctive.size()==0){
                    break;
                }
                cur_top=disjunctive.peek();
            }
            String Snippet="";
            Object[] curr_result=new Object[]{page.get(cur_docid)[0],BM25,cur_docid};
            Queryresult.add(curr_result);
            if(Queryresult.size()>10){
                Queryresult.poll();
            }
            //add new docid to our heap by using the docid we retrieved before
            for (int i=0;i<disjunctive_re.size();i++) {
                int [] previous_list=disjunctive_re.get(i);
                if (did==MaxDocid[previous_list[0]])
                    break;
                did = nextGEQ(lp[previous_list[0]], previous_list[1]+1, Blocknum[previous_list[0]], previous_list[0]);
                if (did<=MaxDocid[previous_list[0]]){
                    disjunctive.add(new int[]{previous_list[0],did});
                    did++;
                }
            }
        }
        return "finished";
    }






    public static double BM25cal(int frequency,int cur_doc_length, int numDoc){
        double temp=Math.log((totalnumofdoc-numDoc+0.5)/(numDoc+0.5));
        double kkkk=totollength/totalnumofdoc;
        double K=1.2*((1-0.75)+0.75*cur_doc_length/(totollength/totalnumofdoc));
        temp=temp*(1.2+1)*frequency/(K+frequency);
        return temp;
    }


    /**
     * this function is used to find the maximum docid in current inverted List
     * @param lp
     * @param Blocknumber
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public static int findMax(DataInputStream lp,int Blocknumber,int index) throws IOException, InterruptedException {
        int cur_block=0;
        int LastDocId=0;
        int BlockSize=0;
        //read whole block
        for (cur_block=0;cur_block<Blocknumber;cur_block++){
            LastDocId=NewreadInt(lp,index);
            BlockSize=NewreadInt(lp,index);
            skipblock(BlockSize,lp);
            //lp.skipBytes(BlockSize);
            //initial[index]=lp.readByte();
        }
        lp.reset();
        //initial[index]=initialCopy[index];
        return LastDocId;
    }

    public static void skipblock(int blocksize,DataInputStream lp) throws IOException {
        lp.skipBytes(blocksize);
    }

    /**
     * this function is used to generate new docid
     * @param in
     * @param docid
     * @param Blocknum
     * @param index
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public static int nextGEQ(DataInputStream in,int docid, int Blocknum, int index) throws IOException, InterruptedException {
        //read the max docid in the block
        int LastDocId=NewreadInt(in,index);
        //read the block size
        int BlockSize=NewreadInt(in,index);
        //initial current possition
        int cur_block=0;
        //skip to target block
        while (docid>LastDocId){
            skipblock(BlockSize,in);
            //in.skipBytes(BlockSize-1);
            //initial[index]=in.readByte();
            cur_block++;
            if(cur_block==Blocknum){
                in.reset();
                //initial[index]=initialCopy[index];
                return LastDocId;
            }
            LastDocId=NewreadInt(in,index);
            BlockSize=NewreadInt(in,index);
        }
        int cur_doc=0;
        int cur_fre=0;
        //check if the block was buffered into our temp before
        if(check[index]!=cur_block||temp[index].size()==0) {
            //sotre all posts in this block into our temp
            temp[index]=new ArrayList<>();
            do {
                cur_doc += NewreadInt(in,index);
                cur_fre = NewreadInt(in,index);
                if(cur_doc<=LastDocId) {
                    int[] result = new int[]{cur_doc, cur_fre};
                    temp[index].add(result);
                }
            } while (cur_doc <= LastDocId);
            check[index]=cur_block;
            temp_index[index]=0;
        }
        //used to prevent reading posts in one block from beginning to the end each time
        // it is only useful when
        int ind=temp_index[index];
        int curr=temp[index].get(temp[index].size()-1)[0];
        while(ind<temp[index].size()&&temp[index].get(ind)[0]<docid){
            ind++;
        }
        temp_index[index]=ind;
        in.reset();
        //initial[index]=initialCopy[index];
        frequency[index]=temp[index].get(ind)[1];

        return temp[index].get(ind)[0];
    }

    public static DataInputStream[] openList(int length) throws FileNotFoundException {
        DataInputStream[] lp=new DataInputStream[length];
        for(int i=0;i<length;i++){
            lp[i]=new DataInputStream(new BufferedInputStream(new FileInputStream("InvertedList.bin")));
        }
        return lp;
    }

    /**
     * This function is used to read complete page table into our hashtable
     * @param page
     * @throws IOException
     */
    public static void readPage(HashMap page) throws IOException {
        DataInputStream is1 = new DataInputStream(new BufferedInputStream(new FileInputStream("pagetable.bin")));
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
                //find the total number of documents in our collection
                totalnumofdoc++;
                //find the total num of words in our collection
                totollength=totollength+length;
            }catch (EOFException e){
                break;
            }
        }
        is1.close();
    }

    /**
     * This function is used to read complete Lexicon into our TreeMap or HashMap
     * @param Lexicon
     * @throws IOException
     */
    public static void readLexicon(TreeMap<String,Object[]> Lexicon)throws IOException {
        DataInputStream is1 = new DataInputStream(new BufferedInputStream(new FileInputStream("Lexicon.bin")));
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


    /**
     * this function is used to read a int value from inverted list
     * it is perform like Var-bytes decoder
     * @param is
     * @param index
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
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

    public static int NewreadInt(DataInputStream is,int index) throws IOException, InterruptedException {
        int currentByte=is.readByte();
        List<Integer>newreadint=new ArrayList<>();
        int result=0;
        while(currentByte<0){
            newreadint.add(currentByte);
            currentByte=is.readByte();
        }
        newreadint.add(currentByte);
        for (int i=0;i<newreadint.size();i++){
            int readVal= newreadint.get(i);
            if (readVal<0) {
                readVal = newreadint.get(i) + 128;
                readVal=readVal<<(newreadint.size()-1-i)*7;
            }
            result+=readVal;
        }
        return result;
    }



    public static String snippetGenerator(int docid,String[] terms) throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection connection= DriverManager.getConnection("jdbc:mysql://localhost:3306/new_schema?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC","root","7516177");
        String query="select * from new_table where id=?";
        PreparedStatement st=connection.prepareStatement(query);
        //Statement st = connection.createStatement();
        st.setInt(1,docid);
        ResultSet rs=st.executeQuery();
        String result="";
        while(rs.next()) {
            result = rs.getString(2);
        }
        int[]cur_position=new int[terms.length];
        String snippet="";
        for (int j=0;j<3;j++) {
            for (int i = 0; i < terms.length; i++) {
                cur_position[i]=result.indexOf(terms[i],cur_position[i]);
            }
            String sub_snippet="";
            for (int i=0;i<terms.length;i++){
                if (cur_position[i]==-1){
                    continue;
                }
                int start=cur_position[i]-20;
                int end=cur_position[i]+50;
                sub_snippet=result.substring(start<0?0:start,end);
                cur_position[i]++;
                snippet=snippet+"..."+sub_snippet+"\n";
            }
        }

        return snippet;
    }


}
