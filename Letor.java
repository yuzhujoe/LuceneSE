import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.lucene.index.Term;



public class Letor {
	
	
	public class docIdRelScorePair{
		String docId;
		double RelScore;
		
		docIdRelScorePair(String docId,double RelScore){
			this.docId = docId;
			this.RelScore = RelScore;
		}
		
	}
	
	public class scoreNumMatchPair{
		double score;
		double match;
		
		scoreNumMatchPair(double score,double match){
			this.score = score;
			this.match = match;
		}
		
	}
	// write the feature vectors to a file that can be read from the SVM
	
	/**
	 * compute the feature vectors from the TermVector
	 * @param trainQryFile: training query file path
	 * @param relJudgeFile: relevance judgment file path
	 */
	public void computeTrainFeature(int[] featureDisableArr,String trainQryFile, String relJudgeFile,String pageRankFile,String trainFeatureVectorFile){
		BufferedReader input = null;
		BufferedReader inputRel = null;
		
		
		String qLine = null;
		
		String rLine = null;
		
		HashMap<Integer,ArrayList<docIdRelScorePair>> hm = new HashMap<>();
		HashMap<String,Double> pageRankHM = buildPageRankMap(pageRankFile);
		ArrayList<ArrayList<Double>> featureList = new ArrayList<>();
		boolean[] featureDisMap = new boolean[18];
		
		if(featureDisableArr!=null){
			for(int i =0;i<featureDisableArr.length;i++){
				featureDisMap[featureDisableArr[i]-1] = true;
			}
		}
		
		for(int i=0;i<18;i++){
			featureList.add(new ArrayList<Double>());
		}
		
		double k_1 = 1.2;
		double k_3 = 0;
		double b =  0.75;
		int mu = 2500;
		double lambda = 0.4;
		
		
 		try {
			//read relevance file first
			inputRel = new BufferedReader(new FileReader(relJudgeFile));
			
			while((rLine = inputRel.readLine())!=null){
				String[] re = rLine.split("\\s");
				int relQid = Integer.parseInt(re[0]);
				// store internal
				String relExternalDocid = re[2];
				int relScore = Integer.parseInt(re[3]);
				if(hm.containsKey(relQid)){
					hm.get(relQid).add(new docIdRelScorePair(relExternalDocid,relScore));
				}else{
					ArrayList<docIdRelScorePair> temp = new ArrayList<>();
					temp.add(new docIdRelScorePair(relExternalDocid,relScore));
					hm.put(relQid, temp);
				}
			}
			
			PrintWriter pr = new PrintWriter(trainFeatureVectorFile);
						
			input = new BufferedReader(new FileReader(trainQryFile));

			while((qLine = input.readLine())!= null){
		        int d = qLine.indexOf(':');

		        if (d < 0) {
		          throw new IllegalArgumentException
		            ("Syntax error:  Missing ':' in query line.");
		        }
		        
		        int qid = Integer.parseInt(qLine.substring(0, d));
		        ArrayList<docIdRelScorePair> pairList= hm.get(qid);
		        
		        
		        String query = qLine.substring(d + 1);
				
		        String[] qryStem = QryEval.tokenizeQuery(query);
		        
		        
		        double[][] featureMaxMin = new double[2][18];
		        
		        // construct a query Stem HashMap
				HashMap<String, Integer> qryStemMap = new HashMap<>();
				
				for(int i= 0;i<qryStem.length;i++){
					qryStemMap.put(qryStem[i], i);
				}
		       
		        int numOfIter = 0;
		        
		        for(docIdRelScorePair pair: pairList){
		        	
		        	//f1: spam Score
		        	int internalDocId = Idx.getInternalDocid(pair.docId);
		        	
		        	double spamScore = Double.parseDouble(Idx.getAttribute ("score",internalDocId));
		        	featureList.get(0).add(spamScore);
		        	
		        	updateMinMax(numOfIter, featureMaxMin,0,spamScore);
		        	
		        	
		        	//extract raw URL
		        	String rawUrl = Idx.getAttribute ("rawUrl", internalDocId);
		        	
		        	//f2: urlDepth
		        	int urlDepth = rawUrl.split("/", -1).length -1;
		        	featureList.get(1).add((double) urlDepth);
		        	
		        	updateMinMax(numOfIter, featureMaxMin,1,(double)urlDepth);
		        	
		        	
		        	
		        	//f3: wiki score
		        	int wikiscore = (rawUrl.indexOf("wikipedia.org")==-1)?0:1;
		        	featureList.get(2).add((double) wikiscore);
		        	
		        	updateMinMax(numOfIter, featureMaxMin,2,(double)wikiscore);

		        	
		        	
		        	//f4: pagerankscore
		        	if(pageRankHM.get(pair.docId)==null){
		        		//System.out.println("Nan for page rank");
		        	}
		        	double pageRankScore = pageRankHM.get(pair.docId)==null?Double.NaN:pageRankHM.get(pair.docId);
		        	
		        	featureList.get(3).add(pageRankScore);
		        	
		        	updateMinMax(numOfIter, featureMaxMin,3,pageRankScore);
		        	
		        	
		        	//f5: BM25 score body
		        	
		        	double bm25BodyScore = featureBM25(qryStemMap,internalDocId,"body",k_1,k_3,b);
		        	featureList.get(4).add(bm25BodyScore);
		        	updateMinMax(numOfIter, featureMaxMin,4,bm25BodyScore);
		        	
		        	
		        	//f6: Indri Score body
		        	scoreNumMatchPair smPair = featureIndri(qryStem, qryStemMap,internalDocId,"body",mu,lambda);
		        	
		        	featureList.get(5).add(smPair.score); 
		        	
		        	updateMinMax(numOfIter, featureMaxMin,5,smPair.score);

		        	
		        	
		        	//f7: TermOverlap for body
		        	double match= smPair.match;
		        	
		        	double bodyOverlap = match/qryStem.length;
		        	
		        	featureList.get(6).add(bodyOverlap);
		        	
		        	updateMinMax(numOfIter, featureMaxMin,6,bodyOverlap);
   	
		        	
		        	//f8: BM25 score title
		        	
		        	double bm25Title = featureBM25(qryStemMap,internalDocId,"title",k_1,k_3,b);
		        	
		        	featureList.get(7).add(bm25Title);
		        	
		        	updateMinMax(numOfIter, featureMaxMin,7,bm25Title);
		        	
		        	
		        	
		        	//f9: Indri Score title
		        	smPair = featureIndri(qryStem, qryStemMap,internalDocId,"title",mu,lambda);
		        
		        	featureList.get(8).add(smPair.score); 
		        	
		        	updateMinMax(numOfIter, featureMaxMin,8,smPair.score);
		        	
		        	
		        	
		        	//f10: TermOverlap for title
		        	match= smPair.match;

		        	double titleOverlap = match/qryStem.length;
		        	
		        	featureList.get(9).add(titleOverlap);
		        	
		        	updateMinMax(numOfIter, featureMaxMin,9,titleOverlap);
		        	

		        	
		        	//f11: BM25 score url
		        	
		        	double bm25Url = featureBM25(qryStemMap,internalDocId,"url",k_1,k_3,b);
		        	
		        	featureList.get(10).add(bm25Url);
		        	
		        	updateMinMax(numOfIter, featureMaxMin,10,bm25Url);
		        	
		        	
		        	
		        	//f12: Indri Score url
		        	
		        	
		        	smPair = featureIndri(qryStem, qryStemMap,internalDocId,"url",mu,lambda);
		        	featureList.get(11).add(smPair.score); 
		        	updateMinMax(numOfIter, featureMaxMin,11,smPair.score);
		        	
		        	
		        	
		        	//f13: TermOverlap for url
		        	match= smPair.match;
		        	
		        	double urlOverlap = match/qryStem.length;
		        	
		        	featureList.get(12).add(urlOverlap);
		        	
		        	updateMinMax(numOfIter, featureMaxMin,12,urlOverlap);
		        	
		        	//f14: BM25 score inlink
		        	
		        	double bm25Inlink = featureBM25(qryStemMap,internalDocId,"inlink",k_1,k_3,b);
		        	featureList.get(13).add(bm25Inlink);
		        	updateMinMax(numOfIter, featureMaxMin,13,bm25Inlink);
		        	
		        	//f15: Indri Score inlink
		        	smPair = featureIndri(qryStem, qryStemMap,internalDocId,"inlink",mu,lambda);
		        	featureList.get(14).add(smPair.score); 
		        	updateMinMax(numOfIter, featureMaxMin,14,smPair.score);
		        	
		        	//f16: TermOverlap for inlink
		        	match= smPair.match;
		        	
		        	double inlinkOverlap = match/qryStem.length;
		        	
		        	featureList.get(15).add(inlinkOverlap);
		        	updateMinMax(numOfIter, featureMaxMin,15,inlinkOverlap);
		        	
		        	//f17 custom 2: tf-idf
		        	double tfidfBody = featureTFIDF(qryStemMap,internalDocId,"body");
		        	featureList.get(16).add(tfidfBody);
		        	updateMinMax(numOfIter, featureMaxMin,16,tfidfBody);
		        	
		        	//f18 custom 2: kl divergence
		        	double klDivergence = featureKLDivergence(qryStem,qryStemMap,internalDocId,"body");
		        	featureList.get(17).add(klDivergence);
		        	updateMinMax(numOfIter, featureMaxMin,17,klDivergence);
		        	
		        	numOfIter++;
		        }
		        System.out.println("Training Query " + qLine);
		        normailzeFeatureList(featureMaxMin,featureList,pr);
		        
		        int numOfDoc = featureList.get(0).size();
		        		        
		        for(int i = 0;i< numOfDoc;i++){
		        	
		        	int rs = (int)(pairList.get(i).RelScore);
		        	pr.print(rs+"\tqid:"+qid+"\t");
		        	//System.out.print(rs+"\tqid:"+qid+"\t");
		        	
		        	for(int j=0;j<18;j++){
		        		if(!featureDisMap[j]){
		        			pr.print((j+1)+":"+featureList.get(j).get(i)+"\t");
		        	//		System.out.print((j+1)+":"+featureList.get(j).get(i)+"\t");
		        		}
		        	}
		        	
		        	pr.print("#\t");
		        	//System.out.print("#\t");
		        	
		        	pr.print(pairList.get(i).docId);
		        	//System.out.print(pairList.get(i).docId);
		        	
		        	pr.print("\n");	
		        	//System.out.print("\n");
		        }
		        
		        for(int i = 0;i<18;i++){
		        	featureList.get(i).clear();
		        }
		        
			}
			
		    pr.close();
			
			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			System.out.println("get Internal ID wrong");
			e.printStackTrace();
		}finally{
			try {
				input.close();
				inputRel.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
		
	private void updateMinMax(int numOfIteration,double[][] featureMaxMin, int index, double score) {
		// TODO Auto-generated method stub
		
		if(!Double.isNaN(score)){
			if(numOfIteration == 0){
				featureMaxMin[0][index] = score;
				featureMaxMin[1][index] = score;
			}else{
				//update max
				if(score > featureMaxMin[1][index]){
					featureMaxMin[1][index] = score;
				}
				//update min
				if(score < featureMaxMin[0][index]){
					featureMaxMin[0][index] = score;
				}
			}
		}
	}

	/**
	 * normalize feature value to [0,1], ignore null value and set them to 0 after normalization
	 * @param featureMinMax
	 * @param featureList
	 * @param pr
	 */
	private void normailzeFeatureList(double[][] featureMinMax,ArrayList<ArrayList<Double>> featureList,PrintWriter pr) {

		for(int i =0;i<18;i++){

			ArrayList<Double> tempList = featureList.get(i);
			
			if(featureMinMax[0][i] != featureMinMax[1][i]){
				double diff = featureMinMax[1][i] - featureMinMax[0][i];
				
				for(int j = 0;j<tempList.size();j++){
					
					if(tempList.get(j).isNaN()){
						tempList.set(j, 0.0);
					}else{
						double val = (tempList.get(j) - featureMinMax[0][i])/diff;
						tempList.set(j, val);
					}
				}
			}else{
				
				for(int j = 0;j<tempList.size();j++){
					tempList.set(j, 0.0);
				}
			}
			
		}

	}

	/**
	 * calculate indri score for a document and return the number of query that can be found in the document
	 * @param qryStem
	 * @param qryStemMap
	 * @param internalDocId
	 * @param field
	 * @param mu
	 * @param lambda
	 * @return
	 */
	private scoreNumMatchPair featureIndri(String[] qryStem,HashMap<String, Integer> qryStemMap,
			int internalDocId, String field, int mu, double lambda) {
		
		double score = 1;
		int numFound = 0;
		boolean[] foundQueryTerm = new boolean[qryStem.length];
		
		try {
			TermVector  tv = new TermVector(internalDocId,field);
				
			int tvLength = tv.stemsLength();
			
			if(tvLength == 0){
				return new scoreNumMatchPair(Double.NaN,Double.NaN);
			}
			
			
			for(int i= 0;i< tvLength;i++){
				String stem = tv.stemString(i);
				if(qryStemMap.containsKey(stem)){
					// Indri Score for the stem
					score *= getScoreIndriFromTermVector(i,tv,mu,lambda,stem,field);
					numFound ++;
					foundQueryTerm[qryStemMap.get(stem)] = true;
				}
			}
			
			//all query term does not match
			if(numFound == 0){
				return new scoreNumMatchPair(0,0);
			}
			
			//partially match
			for(int i = 0;i<qryStem.length;i++){
				if(foundQueryTerm[i] == false){
					score *= getDefaultScoreIndriFromTermVector(tv,mu,lambda,qryStem[i],field);
				}
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		score = Math.pow(score, 1.0/qryStem.length);
		return new scoreNumMatchPair(score,numFound);
	}

	private double getScoreIndriFromTermVector(int index, TermVector tv, double mu,
			double lambda, String stem, String field) throws IOException {
		
		
		double score = 0;  
		
		int tftd = tv.stemFreq(index);
		
		int docLength = tv.positionsLength();
		
		long corpusLength = Idx.getSumOfFieldLengths(field);
		
		long corpusTF = tv.totalStemFreq(index);
		
		double mleProb = (corpusTF*1.0)/corpusLength;
		
		score = (1-lambda)*(tftd+mu*mleProb)/(docLength+mu)+lambda*mleProb;
		
		return score;
	
	}


	private double getDefaultScoreIndriFromTermVector(TermVector tv,
			double mu, double lambda, String stem, String field) throws IOException {
	
		double score = 0;  
		
		double tftd = 0;
		
		int docLength = tv.positionsLength();
		
		long corpusLength = Idx.getSumOfFieldLengths(field);
		
		Term t = new Term(field,stem);
		 
		long corpusTF = Idx.INDEXREADER.totalTermFreq(t);
		
		double mleProb = (corpusTF*1.0)/corpusLength;
		
		score = (1-lambda)*(tftd+mu*mleProb)/(docLength+mu)+lambda*mleProb;
		return score;
	}

	

	public double featureBM25(HashMap<String, Integer> qryStemMap, int internalDocId,
			String field,double k_1,double k_3,double b) {
		
		double score = 0;
		
		try {
			TermVector  tv = new TermVector(internalDocId,field);
		
			int tvLength = tv.stemsLength();
			
			if(tvLength == 0){
				return Double.NaN;
			}
			
			for(int i= 0;i< tvLength;i++){
				String stem = tv.stemString(i);
				if(qryStemMap.containsKey(stem)){
					// BM25 Score for the stem
					score += getScoreBM25FromTermVector(i,tv,k_1,k_3,b,stem,field);
				}
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return score;
	}
	
	public double featureTFIDF(HashMap<String, Integer> qryStemMap, int internalDocId,
			String field) {
		
		double score = 0;
		
		try {
			TermVector  tv = new TermVector(internalDocId,field);
		
			int tvLength = tv.stemsLength();
			
			if(tvLength == 0){
				return Double.NaN;
			}
			
			for(int i= 0;i< tvLength;i++){
				String stem = tv.stemString(i);
				if(qryStemMap.containsKey(stem)){
					score += getScoreTFIDFFromTermVector(i,tv,stem,field);
				}
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return score;
	}
	
	public double featureKLDivergence(String[] qryStem,HashMap<String, Integer> qryStemMap, int internalDocId,
			String field) {
		double score = 0;
		try {
			TermVector  tv = new TermVector(internalDocId,field);
		
			int tvLength = tv.stemsLength();
			
			if(tvLength == 0){
				return Double.NaN;
			}
			
			for(int i= 0;i< tvLength;i++){
				String stem = tv.stemString(i);
				if(qryStemMap.containsKey(stem)){
					score += getScoreKLDivergenceFromTermVector(i,tv,stem,field);
				}
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		score = score/qryStem.length;
		return score;
	}


	/**
	 * 
	 * @param testQryFile
	 * @param testQryScoreListBeforeLetor
	 * @param pageRankFile
	 * @param trainFeatureVectorFile
	 */
	public void computeTestFeatureBM25(int[] featureDisableArr,ArrayList<Integer> testQidArr,String testQryFile, ArrayList<ScoreList> testQryScoreListBeforeLetor, String pageRankFile,String trainFeatureVectorFile){	
		

		BufferedReader input = null;		
		
		String qLine = null;
				
		HashMap<Integer,ArrayList<docIdRelScorePair>> hm = new HashMap<>();
		
		//can use the first one !
		
		HashMap<String,Double> pageRankHM = buildPageRankMap(pageRankFile);
		ArrayList<ArrayList<Double>> featureList = new ArrayList<>();
		
		
		boolean[] featureDisMap = new boolean[18];
		
		if(featureDisableArr!=null){
			for(int i =0;i<featureDisableArr.length;i++){
				featureDisMap[featureDisableArr[i]-1] = true;
			}
		}
		
		
		for(int i=0;i<18;i++){
			featureList.add(new ArrayList<Double>());
		}
		
		double k_1 = 1.2;
		double k_3 = 0;
		double b =  0.75;
		int mu = 2500;
		double lambda = 0.4;
		
		
 		try {
			//read relevance file first
 			
 			// can directly use the scorelist instead of the hashmap
			for(int i = 0;i < testQryScoreListBeforeLetor.size();i++){
				ScoreList sl = testQryScoreListBeforeLetor.get(i);
				
				int relQid = testQidArr.get(i);
				
				for(ScoreList.ScoreListEntry sle: sl.scores){
					
					String relExternalDocid = sle.externalId;
							
					double relScore = sle.score;
					
					if(hm.containsKey(relQid)){
						hm.get(relQid).add(new docIdRelScorePair(relExternalDocid,relScore));
					}else{
						ArrayList<docIdRelScorePair> temp = new ArrayList<>();
						temp.add(new docIdRelScorePair(relExternalDocid,relScore));
						hm.put(relQid, temp);
					}
				}
			}
			
			
			PrintWriter pr = new PrintWriter(trainFeatureVectorFile);
						
			input = new BufferedReader(new FileReader(testQryFile));

			while((qLine = input.readLine())!= null){
		        int d = qLine.indexOf(':');

		        if (d < 0) {
		          throw new IllegalArgumentException
		            ("Syntax error:  Missing ':' in query line.");
		        }
		        
		        int qid = Integer.parseInt(qLine.substring(0, d));
		        ArrayList<docIdRelScorePair> pairList= hm.get(qid);
		        
		        String query = qLine.substring(d + 1);
				
		        String[] qryStem = QryEval.tokenizeQuery(query);
		        
		        
		        double[][] featureMaxMin = new double[2][18];
		        
		        // construct a query Stem HashMap
				HashMap<String, Integer> qryStemMap = new HashMap<>();
				
				for(int i= 0;i<qryStem.length;i++){
					qryStemMap.put(qryStem[i], i);
				}
		       
		        int numOfIter = 0;
		        
		        for(docIdRelScorePair pair: pairList){
		        	String[] tempFeatureVector = new String[21];
		        	
		        	//relevant score 
		        	tempFeatureVector[0] = ""+pair.RelScore;
		        	
		        	// qid
		        	tempFeatureVector[1] = ""+qid;
		        	
		        	//f1: spam Score
		        	int internalDocId = Idx.getInternalDocid(pair.docId);
		        	
		        	double spamScore = Double.parseDouble(Idx.getAttribute ("score",internalDocId));
		        	featureList.get(0).add(spamScore);
		        	
		        	updateMinMax(numOfIter, featureMaxMin,0,spamScore);
		        	
		        	
		        	//extract raw URL
		        	String rawUrl = Idx.getAttribute ("rawUrl", internalDocId);
		        	
		        	//f2: urlDepth
		        	int urlDepth = rawUrl.split("/", -1).length -1;
		        	featureList.get(1).add((double) urlDepth);
		        	
		        	updateMinMax(numOfIter, featureMaxMin,1,(double)urlDepth);
		        	
		        	
		        	
		        	//f3: wiki score
		        	int wikiscore = (rawUrl.indexOf("wikipedia.org")==-1)?0:1;
		        	featureList.get(2).add((double) wikiscore);
		        	
		        	updateMinMax(numOfIter, featureMaxMin,2,(double)wikiscore);

		        	
		        	
		        	//f4: pagerankscore
		        	if(pageRankHM.get(pair.docId)==null){
		        		//System.out.println("Nan for page rank");
		        	}
		        	double pageRankScore = pageRankHM.get(pair.docId)==null?Double.NaN:pageRankHM.get(pair.docId);
		        	
		        	featureList.get(3).add(pageRankScore);
		        	
		        	updateMinMax(numOfIter, featureMaxMin,3,pageRankScore);
		        	
		        	
		        	//f5: BM25 score body
		        	
		        	double bm25BodyScore = featureBM25(qryStemMap,internalDocId,"body",k_1,k_3,b);
		        	featureList.get(4).add(bm25BodyScore);
		        	updateMinMax(numOfIter, featureMaxMin,4,bm25BodyScore);
		        	
		        	
		        	//f6: Indri Score body
		        	scoreNumMatchPair smPair = featureIndri(qryStem, qryStemMap,internalDocId,"body",mu,lambda);
		        	
		        	featureList.get(5).add(smPair.score); 
		        	
		        	updateMinMax(numOfIter, featureMaxMin,5,smPair.score);

		        	
		        	
		        	//f7: TermOverlap for body
		        	double match= smPair.match;
		        	
		        	double bodyOverlap = match/qryStem.length;
		        	
		        	featureList.get(6).add(bodyOverlap);
		        	
		        	updateMinMax(numOfIter, featureMaxMin,6,bodyOverlap);
   	
		        	
		        	//f8: BM25 score title
		        	
		        	double bm25Title = featureBM25(qryStemMap,internalDocId,"title",k_1,k_3,b);
		        	
		        	featureList.get(7).add(bm25Title);
		        	
		        	updateMinMax(numOfIter, featureMaxMin,7,bm25Title);
		        	
		        	
		        	
		        	//f9: Indri Score title
		        	smPair = featureIndri(qryStem, qryStemMap,internalDocId,"title",mu,lambda);
		        
		        	featureList.get(8).add(smPair.score); 
		        	
		        	updateMinMax(numOfIter, featureMaxMin,8,smPair.score);
		        	
		        	
		        	
		        	//f10: TermOverlap for title
		        	match= smPair.match;

		        	double titleOverlap = match/qryStem.length;
		        	
		        	featureList.get(9).add(titleOverlap);
		        	
		        	updateMinMax(numOfIter, featureMaxMin,9,titleOverlap);
		        	

		        	
		        	//f11: BM25 score url
		        	
		        	double bm25Url = featureBM25(qryStemMap,internalDocId,"url",k_1,k_3,b);
		        	
		        	featureList.get(10).add(bm25Url);
		        	
		        	updateMinMax(numOfIter, featureMaxMin,10,bm25Url);
		        	
		        	
		        	
		        	//f12: Indri Score url
		        	
		        	
		        	smPair = featureIndri(qryStem, qryStemMap,internalDocId,"url",mu,lambda);
		        	featureList.get(11).add(smPair.score); 
		        	updateMinMax(numOfIter, featureMaxMin,11,smPair.score);
		        	
		        	
		        	
		        	//f13: TermOverlap for url
		        	match= smPair.match;
		        	
		        	double urlOverlap = match/qryStem.length;
		        	
		        	featureList.get(12).add(urlOverlap);
		        	
		        	updateMinMax(numOfIter, featureMaxMin,12,urlOverlap);
		        	
		        	
		        	
		        	//f14: BM25 score inlink
		        	
		        	
		        	double bm25Inlink = featureBM25(qryStemMap,internalDocId,"inlink",k_1,k_3,b);
		        	featureList.get(13).add(bm25Inlink);
		        	updateMinMax(numOfIter, featureMaxMin,13,bm25Inlink);

		        	
		        	
		        	//f15: Indri Score inlink
		        	smPair = featureIndri(qryStem, qryStemMap,internalDocId,"inlink",mu,lambda);
		        	featureList.get(14).add(smPair.score); 
		        	updateMinMax(numOfIter, featureMaxMin,14,smPair.score);
		        	
		        	//f16: TermOverlap for inlink
		        	match= smPair.match;
		        	
		        	double inlinkOverlap = match/qryStem.length;
		        	
		        	featureList.get(15).add(inlinkOverlap);
		        	updateMinMax(numOfIter, featureMaxMin,15,inlinkOverlap);
		        	
		        	
		        	//f17 custom 2: tf-idf
		        	double tfidfBody = featureTFIDF(qryStemMap,internalDocId,"body");
		        	featureList.get(16).add(tfidfBody);
		        	updateMinMax(numOfIter, featureMaxMin,16,tfidfBody);
		        	
		        	//f18 custom 2: kl divergence
		        	double klDivergenceBody = featureKLDivergence(qryStem,qryStemMap,internalDocId,"body");
		        	featureList.get(17).add(klDivergenceBody);
		        	updateMinMax(numOfIter, featureMaxMin,17,klDivergenceBody);
		        	
		        	numOfIter++;
		        }
		        System.out.println("Testing Query " + qLine);
		        normailzeFeatureList(featureMaxMin,featureList,pr);
		        
		        int numOfDoc = featureList.get(0).size();
		        		        
		        for(int i = 0;i< numOfDoc;i++){
		        	
		        	double rs = pairList.get(i).RelScore;
		        	pr.print(rs+"\tqid:"+qid+"\t");
		        	
		        	for(int j=0;j<18;j++){
		        		if(!featureDisMap[j]){
		        			pr.print((j+1)+":"+featureList.get(j).get(i)+"\t");
		        		}
		        	}	        	
		        	pr.print("#\t");
		        	pr.print(pairList.get(i).docId);
		        	pr.print("\n");	
		        }
		        
		        for(int i = 0;i<18;i++){
		        	featureList.get(i).clear();
		        }
		        
			}
		    pr.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			System.out.println("get Internal ID wrong");
			e.printStackTrace();
		}finally{
			try {
				input.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public HashMap<String, Double> buildPageRankMap(String pageRkFilePath){
		
		HashMap<String, Double> hm = new HashMap<>();
		try{
			BufferedReader br = new BufferedReader(new FileReader(pageRkFilePath));
			String line = null;
			
			while((line=br.readLine())!= null){
				String[] t = line.split("\\s");
				hm.put(t[0],Double.parseDouble(t[1]));
			}
			
			br.close();
		}
		catch(IOException e){
			e.printStackTrace();
		}
		return hm;

	}
	
	public double getScoreBM25FromTermVector (int index,TermVector tv,double k_1,double k_3,double b,String stem,String field) throws IOException {
	    long N = Idx.getNumDocs();
	    	
	    int df = tv.stemDf(index);
	    			    	
		int tftd = tv.stemFreq(index);
	    	
		//need to get field from outside
		int docLength = tv.positionsLength();
	
		double avgDocLength =  (double)(Idx.getSumOfFieldLengths(field)) / (double)(Idx.getDocCount (field));
	
	    	
		// Attention: not calculate qtf!!!!!
		double score = Math.max(0.0,Math.log((N-df+0.5)/(df+0.5)))*((double)tftd)/(((double)tftd)+k_1*((1.0-b)+b*((double)docLength)/((double)avgDocLength)));
		return score;
		    	
	}
	
	
	public double getScoreTFIDFFromTermVector (int index,TermVector tv,String stem,String field) throws IOException {
	    long N = Idx.getNumDocs();
	    	
	    int df = tv.stemDf(index);
	    			    	
		int tftd = tv.stemFreq(index);
		double idf = Math.log(1+((double)N)/(df+0.5));
	    		    	
		double score = idf*(Math.log(1+tftd));
		return score;
	}
	
	public double getScoreKLDivergenceFromTermVector (int index,TermVector tv,String stem,String field) throws IOException {
	    		    			    	
		int tftd = tv.stemFreq(index);
				
		int docLength = tv.positionsLength();		    	
		
		double score = Math.log(1+(double)(tftd)/docLength);
		return score;
	}
	
	
	public void runSVM(String execPath,String svmC,String qrelsFeatureOutputFile,String modelOutputFile) throws Exception{
		
	    // runs svm_rank_learn from within Java to train the model
	    // execPath is the location of the svm_rank_learn utility, 
	    // which is specified by letor:svmRankLearnPath in the parameter file.
	    // FEAT_GEN.c is the value of the letor:c parameter.
	    Process cmdProc = Runtime.getRuntime().exec(
	        new String[] { execPath, "-c", String.valueOf(svmC), qrelsFeatureOutputFile,
	            modelOutputFile });

	    // The stdout/stderr consuming code MUST be included.
	    // It prevents the OS from running out of output buffer space and stalling.

	    // consume stdout and print it out for debugging purposes
	    BufferedReader stdoutReader = new BufferedReader(
	        new InputStreamReader(cmdProc.getInputStream()));
	    String line;
	    while ((line = stdoutReader.readLine()) != null) {
	      System.out.println(line);
	    }
	    // consume stderr and print it for debugging purposes
	    BufferedReader stderrReader = new BufferedReader(
	        new InputStreamReader(cmdProc.getErrorStream()));
	    while ((line = stderrReader.readLine()) != null) {
	      System.out.println(line);
	    }

	    // get the return value from the executable. 0 means success, non-zero 
	    // indicates a problem
	    int retValue = cmdProc.waitFor();
	    if (retValue != 0) {
	      throw new Exception("SVM Rank crashed.");
	    }
	    
	}
	
	public void runSVMClassify(String execPath,String qrelsFeatureOutputFile,String modelOutputFile,String classifyOutputFile) throws Exception{
		
	    // runs classify from within Java to train the model
	    // execPath is the location of the svm_rank_learn utility, 
	    // which is specified by letor:svmRankLearnPath in the parameter file.
	    Process cmdProc = Runtime.getRuntime().exec(
	        new String[] { execPath, qrelsFeatureOutputFile,
	            modelOutputFile, classifyOutputFile});

	    // The stdout/stderr consuming code MUST be included.
	    // It prevents the OS from running out of output buffer space and stalling.

	    // consume stdout and print it out for debugging purposes
	    BufferedReader stdoutReader = new BufferedReader(
	        new InputStreamReader(cmdProc.getInputStream()));
	    String line;
	    while ((line = stdoutReader.readLine()) != null) {
	      System.out.println(line);
	    }
	    // consume stderr and print it for debugging purposes
	    BufferedReader stderrReader = new BufferedReader(
	        new InputStreamReader(cmdProc.getErrorStream()));
	    while ((line = stderrReader.readLine()) != null) {
	      System.out.println(line);
	    }

	    // get the return value from the executable. 0 means success, non-zero 
	    // indicates a problem
	    int retValue = cmdProc.waitFor();
	    if (retValue != 0) {
	      throw new Exception("SVM Rank crashed.");
	    }
	    
	}
}
