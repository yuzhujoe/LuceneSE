/*
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.1.
 */

import java.io.*;
import java.util.*;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.lucene.index.TermsEnum;


/**
 * QryEval is a simple application that reads queries from a file,
 * evaluates them against an index, and writes the results to an
 * output file.  This class contains the main method, a method for
 * reading parameter and query files, initialization methods, a simple
 * query parser, a simple query processor, and methods for reporting
 * results.
 * <p>
 * This software illustrates the architecture for the portion of a
 * search engine that evaluates queries.  It is a guide for class
 * homework assignments, so it emphasizes simplicity over efficiency.
 * Everything could be done more efficiently and elegantly.
 * <p>
 * The {@link Qry} hierarchy implements query evaluation using a
 * 'document at a time' (DaaT) methodology.  Initially it contains an
 * #OR operator for the unranked Boolean retrieval model and a #SYN
 * (synonym) operator for any retrieval model.  It is easily extended
 * to support additional query operators and retrieval models.  See
 * the {@link Qry} class for details.
 * <p>
 * The {@link RetrievalModel} hierarchy stores parameters and
 * information required by different retrieval models.  Retrieval
 * models that need these parameters (e.g., BM25 and Indri) use them
 * very frequently, so the RetrievalModel class emphasizes fast access.
 * <p>
 * The {@link Idx} hierarchy provides access to information in the
 * Lucene index.  It is intended to be simpler than accessing the
 * Lucene index directly.
 * <p>
 * As the search engine becomes more complex, it becomes useful to
 * have a standard approach to representing documents and scores.
 * The {@link ScoreList} class provides this capability.
 */
public class QryEval {

  //  --------------- Constants and variables ---------------------

  private static final String USAGE =
    "Usage:  java QryEval paramFile\n\n";

  private static final EnglishAnalyzerConfigurable ANALYZER =
    new EnglishAnalyzerConfigurable(Version.LUCENE_43);
  private static final String[] TEXT_FIELDS =
    { "body", "title", "url", "inlink" };


  //  --------------- Methods ---------------------------------------

  /**
   * @param args The only argument is the parameter file name.
   * @throws Exception Error accessing the Lucene index.
   */
  public static void main(String[] args) throws Exception {

    //  This is a timer that you may find useful.  It is used here to
    //  time how long the entire program takes, but you can move it
    //  around to time specific parts of your code.
    
    Timer timer = new Timer();
    timer.start ();

    //  Check that a parameter file is included, and that the required
    //  parameters are present.  Just store the parameters.  They get
    //  processed later during initialization of different system
    //  components.

    if (args.length < 1) {
      throw new IllegalArgumentException (USAGE);
    }

    Map<String, String> parameters = readParameterFile (args[0]);

    //  Configure query lexical processing to match index lexical
    //  processing.  Initialize the index and retrieval model.

    ANALYZER.setLowercase(true);
    ANALYZER.setStopwordRemoval(true);
    ANALYZER.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);

    Idx.initialize (parameters.get ("indexPath"));
    
    //nov22 add letor
    if(parameters.get("retrievalAlgorithm").equals("letor")){
    	//peoform letor
    	
    	runLetor(parameters);
    	
    	return;
    }
    
    
    
    // Nov 22: below is from HW1
    RetrievalModel model = initializeRetrievalModel (parameters);

    //  Perform experiments.
    
    processQueryFile(parameters.get("queryFilePath"), model, parameters);

    //  Clean up.
    
    timer.stop ();
    System.out.println ("Time:  " + timer);
  }

  private static void runLetor(Map<String, String> parameters) throws Exception{
	// TODO Auto-generated method stub
	   	//Step1: generate training feature vector, also perform normalization
  	
  	String featureDisable = parameters.get("letor:featureDisable");
  	
  	int[] featureDisableArr = null;
  	
  	if(featureDisable!=null){
	    String[] featureDisableStrArr = featureDisable.split(",");
	    featureDisableArr = new int[featureDisableStrArr.length];
	    	
	    for(int i = 0;i< featureDisableArr.length;i++){
	    	featureDisableArr[i] = Integer.parseInt(featureDisableStrArr[i]);
	    }
  	}
  	
  	Letor lt = new Letor();
  	
  	lt.computeTrainFeature(featureDisableArr,parameters.get("letor:trainingQueryFile"),parameters.get("letor:trainingQrelsFile"),parameters.get("letor:pageRankFile"),parameters.get("letor:trainingFeatureVectorsFile"));
  	

  	//Step2: call SVM^(rank) to perform training, input is the feature vectors
  	
  	lt.runSVM(parameters.get("letor:svmRankLearnPath"), parameters.get("letor:svmRankParamC"),parameters.get("letor:trainingFeatureVectorsFile"),parameters.get("letor:svmRankModelFile"));
  	
  	System.out.println("finish training, begin generate testing result");
  	    	
  	//Step3: generate testing feature vector, also perform normalization
  	
  	Map<String, String> testParameters = new HashMap<String,String>();
  	testParameters.put("retrievalAlgorithm", "bm25");
  	testParameters.put("BM25:k_1", "1.2");
  	testParameters.put("BM25:k_3", "0");
  	testParameters.put("BM25:b", "0.75");
  	
  	
  	RetrievalModel model = initializeRetrievalModel (testParameters);
  	
  	testParameters.put("trecEvalOutputPath", parameters.get("trecEvalOutputPath"));
  	
  	
  	ArrayList<ScoreList> testQryScoreListBeforeLetor  = processTestQueryFileLetor(parameters.get("queryFilePath"), model, testParameters);
  	
  	ArrayList<Integer> testQidArr = buildQidArray(parameters.get("queryFilePath"));
  	
  	lt.computeTestFeatureBM25(featureDisableArr,testQidArr,parameters.get("queryFilePath"),testQryScoreListBeforeLetor,parameters.get("letor:pageRankFile"),parameters.get("letor:testingFeatureVectorsFile"));

  	System.out.println("finish generate testing result");
  	
  	
  	
  	//Step4: call SVM^(rank) to produce score for the testing feature vectors
  	lt.runSVMClassify(parameters.get("letor:svmRankClassifyPath"), parameters.get("letor:testingFeatureVectorsFile"),parameters.get("letor:svmRankModelFile"),parameters.get("letor:testingDocumentScores"));
      
  	//return;
  	//Step5: read in the scores and re-rank the initial ranking
  	rerankSVMOutput(testQryScoreListBeforeLetor ,parameters.get("letor:testingDocumentScores"));
  	
  	//Step6: output the re-rank into trec-eval format
  	
  	PrintWriter outputReRank = new PrintWriter(parameters.get("trecEvalOutputPath"));
  	
  	for(int i=0;i<testQryScoreListBeforeLetor.size();i++){
  		ScoreList tempSL = testQryScoreListBeforeLetor.get(i);
  		printResults(""+testQidArr.get(i), tempSL, parameters,outputReRank);
  		outputReRank.flush();
  	}
  	
  	outputReRank.close();
  	testQryScoreListBeforeLetor = null;
}

private static void rerankSVMOutput(ArrayList<ScoreList> testQryScoreListBeforeLetor, String scoreFile) throws IOException{
	// TODO Auto-generated method stub
	BufferedReader scoreBR = new BufferedReader(new FileReader(scoreFile));
	
	double newScore;
	
	for(int i =0;i<testQryScoreListBeforeLetor.size();i++){
		ScoreList tempList = testQryScoreListBeforeLetor.get(i);
		for(int j=0;j< tempList.size();j++){
			newScore = Double.parseDouble(scoreBR.readLine());
			tempList.setDocidScore(j, newScore);
		}
		tempList.sort();
	}
	scoreBR.close();
	return;
}

private static ArrayList<Integer> buildQidArray(String qryFile) throws IOException {
	// TODO Auto-generated method stub
	BufferedReader input = new BufferedReader(new FileReader(qryFile));
	
	ArrayList<Integer> result = new ArrayList<Integer>();
	 
	String qLine = null;
	while((qLine = input.readLine())!= null){
        int d = qLine.indexOf(':');

        if (d < 0) {
          throw new IllegalArgumentException
            ("Syntax error:  Missing ':' in query line.");
        }
        
        int qid = Integer.parseInt(qLine.substring(0, d));
        result.add(qid);
	}
	
	return result;
}

private static ArrayList<ScoreList> processTestQueryFileLetor(String queryFilePath,
		RetrievalModel model, Map<String, String> parameters) throws IOException{
	// TODO Auto-generated method stub
	
	ArrayList<ScoreList> result = new ArrayList<>();
	
    BufferedReader input = null;
    
    try {
      String qLine = null;

      input = new BufferedReader(new FileReader(queryFilePath));
            
      //  Each pass of the loop processes one query.
  	  // Nov 4th, open the expansionQryFile once and close it after process all query

  	  
      while ((qLine = input.readLine()) != null) {
        int d = qLine.indexOf(':');

        if (d < 0) {
          throw new IllegalArgumentException
            ("Syntax error:  Missing ':' in query line.");
        }

        printMemoryUsage(false);

        String qid = qLine.substring(0, d);
        String query = qLine.substring(d + 1);

        System.out.println("Query " + qLine);
        
        ScoreList r = null;
        
        r = processQuery(query, model);
        
        if (r != null) {
          sortResults(r);
          r.truncate(100);
          // add this scorelist to the ArrayList
          
          // lost the qid????
          
          result.add(r);
          
          //use the first 100 result!!!! for reranking 
          System.out.println("finish generate initial BM25 result");
        }
      }
          
    } catch (IOException ex) {
      ex.printStackTrace();
    } catch (Exception e) {
		// TODO Auto-generated catch block
    	System.out.println("get Internal Doc Id wrong!");
		e.printStackTrace();
	} finally {
      input.close();
    }
  
	return result;
}

/**
   * Allocate the retrieval model and initialize it using parameters
   * from the parameter file.
   * @return The initialized retrieval model
   * @throws IOException Error accessing the Lucene index.
   */
  //need add ranked boolean model 
  private static RetrievalModel initializeRetrievalModel (Map<String, String> parameters)
    throws IOException {

    RetrievalModel model = null;
    String modelString = parameters.get ("retrievalAlgorithm").toLowerCase();

    if (modelString.equals("unrankedboolean")) {
      model = new RetrievalModelUnrankedBoolean();
    }else if (modelString.equals("rankedboolean")){
      model = new RetrievalModelRankedBoolean();
    }else if (modelString.equals("bm25")){
    	double k_1 = Double.parseDouble(parameters.get("BM25:k_1"));
    	double k_3 = Double.parseDouble(parameters.get("BM25:k_3"));
    	double b = Double.parseDouble(parameters.get("BM25:b"));
    	model = new RetrievalModelBM25(k_1, k_3, b);
    }else if (modelString.equals("indri")){
    	double mu = Double.parseDouble(parameters.get("Indri:mu"));
    	double lambda = Double.parseDouble(parameters.get("Indri:lambda"));
    	model = new RetrievalModelIndri(mu,lambda);
    }else {
      throw new IllegalArgumentException
        ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
    }
    return model;
  }

  /**
   * Return a query tree that corresponds to the query.
   * 
   * @param qString
   *          A string containing a query.
   * @param qTree
   *          A query tree
   * @throws IOException Error accessing the Lucene index.
   */
  static Qry parseQuery(String qString, RetrievalModel model) throws IOException {

    //  Add a default query operator to every query. This is a tiny
    //  bit of inefficiency, but it allows other code to assume
    //  that the query will return document ids and scores.

    String defaultOp = model.defaultQrySopName ();
    qString = defaultOp + "(" + qString + ")";

    //  Simple query tokenization.  Terms like "near-death" are handled later.

    StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()", true);
    String token = null;

    //  This is a simple, stack-based parser.  These variables record
    //  the parser's state.
    
    Qry currentOp = null;
    Stack<Qry> opStack = new Stack<Qry>();
    boolean weightExpected = false;
    Stack<Double> weightStack = new Stack<Double>();
    double weight;

    //  Each pass of the loop processes one token. The query operator
    //  on the top of the opStack is also stored in currentOp to
    //  make the code more readable.

    while (tokens.hasMoreTokens()) {

      token = tokens.nextToken();

      if (token.matches("[ ,(\t\n\r]")) {
        continue;
      } else if (token.equals(")")) {	// Finish current query op.

        // If the current query operator is not an argument to another
        // query operator (i.e., the opStack is empty when the current
        // query operator is removed), we're done (assuming correct
        // syntax - see below).

        opStack.pop();

        if (opStack.empty())
          break;

	    // Not done yet.  Add the current operator as an argument to
        // the higher-level operator, and shift processing back to the
        // higher-level operator.

        Qry arg = currentOp;
        currentOp = opStack.peek();
        
        //if the current operator does not have any argument, then the weight of this operator should be removed
        if(arg.args.size()!=0)
        	currentOp.appendArg(arg);
        else{
           	 weightStack.pop();
        }
        
        // if the current operator is a wand or wsum then add the weight to this operator
        // pop the weight from the weight stack
    	if ((currentOp instanceof QrySopWand)){
    		if(arg.args.size()!=0){
    			weight = weightStack.pop();
    			((QrySopWand)currentOp).addWeight(weight);
    		}
    		weightExpected = true;
    	}
    	if ((currentOp instanceof QrySopWsum)){
    		if(arg.args.size()!=0){
    			weight = weightStack.pop();
    			((QrySopWsum)currentOp).addWeight(weight);
    		}
    		weightExpected = true;
    	}

      } else if (token.equalsIgnoreCase("#or")) {
        currentOp = new QrySopOr ();
        currentOp.setDisplayName (token);
        opStack.push(currentOp);
        weightExpected = false;
      } else if (token.equalsIgnoreCase("#and")){
    	currentOp = new QrySopAnd ();
    	currentOp.setDisplayName(token);
    	opStack.push(currentOp);
    	weightExpected = false;
      } else if (token.toLowerCase().startsWith("#near")){
    	int idx = token.indexOf("/");
    	if (idx == -1){
    		throw new IllegalArgumentException ("Error: no k is specified for NEAR operator " + token);
    	}
    	if(!token.substring(idx+1).matches("[1-9][0-9]*"))
    	  throw new IllegalArgumentException ("Error: k should be positive integer");
    	  int k = Integer.parseInt(token.substring(idx+1)); 
    	  currentOp = new QryIopNear (k);
      	  currentOp.setDisplayName(token);
      	  opStack.push(currentOp);
      	  weightExpected = false;
      } else if(token.toLowerCase().startsWith("#window")){
      	  int idx = token.indexOf("/");
      	  if (idx == -1){
      		throw new IllegalArgumentException ("Error: no k is specified for WINDOW operator " + token);
      	  }
      	  if(!token.substring(idx+1).matches("[1-9][0-9]*"))
      	    throw new IllegalArgumentException ("Error: k should be positive integer");
      	  int k = Integer.parseInt(token.substring(idx+1)); 
      	  currentOp = new QryIopWindow (k);
          currentOp.setDisplayName(token);
          opStack.push(currentOp);
          weightExpected = false;
      } else if (token.equalsIgnoreCase("#syn")) {
        currentOp = new QryIopSyn();
        currentOp.setDisplayName (token);
        opStack.push(currentOp);
        weightExpected = false;
      } else if (token.equalsIgnoreCase("#sum")) {
    	currentOp = new QrySopSum();
        currentOp.setDisplayName (token);
        opStack.push(currentOp);
        weightExpected = false;
      } else if(token.equalsIgnoreCase("#wand")){
    	currentOp = new QrySopWand();
    	currentOp.setDisplayName(token);
    	opStack.push(currentOp);
    	weightExpected = true;
      } else if(token.equalsIgnoreCase("#wsum")){
    	currentOp = new QrySopWsum();
    	currentOp.setDisplayName(token);
    	opStack.push(currentOp);
    	weightExpected = true;
      }
      //Oct 20, calculate for weight
      else {

    	if(weightExpected){
    		weight = Double.parseDouble(token);
    		weightStack.push(weight);
    		weightExpected = false;
    	}
    	else{
    	   	//  Split the token into a term and a field.
            int delimiter = token.indexOf('.');
            String field = null;
            String term = null;

            if (delimiter < 0) {
              field = "body";
              term = token;
            } else {
              field = token.substring(delimiter + 1).toLowerCase();
              term = token.substring(0, delimiter);
            }
            
            // Sep 15 field problem
            if ((field.compareTo("url") != 0) &&
    	    (field.compareTo("keywords") != 0) &&
    	    (field.compareTo("title") != 0) &&
    	    (field.compareTo("body") != 0) &&
                (field.compareTo("inlink") != 0)) {
              throw new IllegalArgumentException ("Error: Unknown field " + token);
            }

            //  Lexical processing, stopwords, stemming.  A loop is used
            //  just in case a term (e.g., "near-death") gets tokenized into
            //  multiple terms (e.g., "near" and "death").

            String t[] = tokenizeQuery(term);
            
            for (int j = 0; j < t.length; j++) {
            	Qry termOp = new QryIopTerm(t[j], field);
            	
            	// 0.4 * near-death become 0.4 * near + 0.4 * death
   
            	if ((currentOp instanceof QrySopWand)){
                    //Oct 21 use weight Stack
                    Double termWeight = weightStack.pop();
            		((QrySopWand)currentOp).addWeight(termWeight);
            	}
            	if ((currentOp instanceof QrySopWsum)){
                    //Oct 21 use weight Stack
                    Double termWeight = weightStack.pop();
            		((QrySopWsum)currentOp).addWeight(termWeight);
            	}
            	currentOp.appendArg(termOp);
            }
            

            //Oct 21 change weight Expected value
            if ((currentOp instanceof QrySopWand) || (currentOp instanceof QrySopWsum) ){
                weightExpected = true;
            }
            else{
            	weightExpected = false;
            }

    	}
 
      }
    }


    //  A broken structured query can leave unprocessed tokens on the opStack,

    if (tokens.hasMoreTokens()) {
      throw new IllegalArgumentException
        ("Error:  Query syntax is incorrect.  " + qString);
    }

    return currentOp;
  }

  /**
   * Remove degenerate nodes produced during query parsing, for
   * example #NEAR/1 (of the) that can't possibly match. It would be
   * better if those nodes weren't produced at all, but that would
   * require a stronger query parser.
   */
  static boolean parseQueryCleanup(Qry q) {

    boolean queryChanged = false;

    // Iterate backwards to prevent problems when args are deleted.

    for (int i = q.args.size() - 1; i >= 0; i--) {

      Qry q_i = q.args.get(i);

      // All operators except TERM operators must have arguments.
      // These nodes could never match.
      
      if ((q_i.args.size() == 0) &&
	  (! (q_i instanceof QryIopTerm))) {
        q.removeArg(i);
        queryChanged = true;
      } else 

	// All operators (except SCORE operators) must have 2 or more
	// arguments. This improves efficiency and readability a bit.
	// However, be careful to stay within the same QrySop / QryIop
	// subclass, otherwise the change might cause a syntax error.
	
	if ((q_i.args.size() == 1) &&
	    (! (q_i instanceof QrySopScore))) {

	  Qry q_i_0 = q_i.args.get(0);

	  if (((q_i instanceof QrySop) && (q_i_0 instanceof QrySop)) ||
	      ((q_i instanceof QryIop) && (q_i_0 instanceof QryIop))) {
	    q.args.set(i, q_i_0);
	    queryChanged = true;
	  }
	} else

	  // Check the subtree.
	  
	  if (parseQueryCleanup (q_i))
	    queryChanged = true;
    }

    return queryChanged;
  }

  /**
   * Print a message indicating the amount of memory used. The caller
   * can indicate whether garbage collection should be performed,
   * which slows the program but reduces memory usage.
   * 
   * @param gc
   *          If true, run the garbage collector before reporting.
   */
  public static void printMemoryUsage(boolean gc) {

    Runtime runtime = Runtime.getRuntime();

    if (gc)
      runtime.gc();

    System.out.println("Memory used:  "
        + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
  }

  /**
   * Process one query.
   * @param qString A string that contains a query.
   * @param model The retrieval model determines how matching and scoring is done.
   * @return Search results
   * @throws IOExceptfion Error accessing the index
   */
  static ScoreList processQuery(String qString, RetrievalModel model)
    throws IOException {

    Qry q = parseQuery(qString, model);

    // Optimize the query.  Remove query operators (except SCORE
    // operators) that have only 1 argument. This improves efficiency
    // and readability a bit.

    if (q.args.size() == 1) {
      Qry q_0 = q.args.get(0);

      if (q_0 instanceof QrySop) {
	q = q_0;
      }
    }

    while ((q != null) && parseQueryCleanup(q));

    // Show the query that is evaluated

    System.out.println("    --> " + q);
    
    if (q != null) {

      ScoreList r = new ScoreList ();
      
      if (q.args.size () > 0) {		// Ignore empty queries

        q.initialize (model);
      //  System.out.println(q.getClass());

        while (q.docIteratorHasMatch (model)) {
          
          int docid = q.docIteratorGetMatch ();
          
         // System.out.println("docid: "+ docid);
          double score = ((QrySop) q).getScore (model);
          r.add (docid, score);
          q.docIteratorAdvancePast (docid);
        }
      }

      return r;
    } else
      return null;
  }

  /**
   * Process the query file.
   * @param queryFilePath
   * @param model
   * @throws IOException Error accessing the Lucene index.
   */
  static void processQueryFile(String queryFilePath,
                               RetrievalModel model, Map<String, String> parameters)
      throws IOException {

    BufferedReader input = null;
    PrintWriter output = null; 
    		
    try {
      String qLine = null;

      input = new BufferedReader(new FileReader(queryFilePath));
      
  	  String outputFileName = parameters.get("trecEvalOutputPath");
  	  output = new PrintWriter(outputFileName);
      
      //  Each pass of the loop processes one query.
  	  // Nov 4th, open the expansionQryFile once and close it after process all query
  	  String fbExpansionQueryFile = parameters.get("fbExpansionQueryFile");
  	  
  	  PrintWriter pwExandedQry = null;
  	
  	  if(fbExpansionQueryFile!=null){
  		 pwExandedQry = new PrintWriter(fbExpansionQueryFile);
  	  }
  	  
      while ((qLine = input.readLine()) != null) {
        int d = qLine.indexOf(':');

        if (d < 0) {
          throw new IllegalArgumentException
            ("Syntax error:  Missing ':' in query line.");
        }

        printMemoryUsage(false);

        String qid = qLine.substring(0, d);
        String query = qLine.substring(d + 1);

        System.out.println("Query " + qLine);

        ScoreList r = null;
        
        //Nov 1 Add feedback
        //Task 1: read from the existed ranking file to generated expanded query
        //Task 2: use the first retrieval result to generate expanded query

        String fb = parameters.get("fb");
        
        
        if(fb==null||fb.compareTo("false")==0){
            r = processQuery(query, model);
        }
        else{
        	System.out.println("feed back is true");
        	String expandedQry = null;
        	String fbInitialRankingFile = parameters.get("fbInitialRankingFile");
        	int fbTerms = Integer.parseInt(parameters.get("fbTerms"));
        	int fbDocs = Integer.parseInt(parameters.get("fbDocs"));
        	int fbMu = Integer.parseInt(parameters.get("fbMu"));
        	
        	
        	if(fbInitialRankingFile!=null){
        		//read from the initial ranking file 
        		expandedQry = generateExpanedQueryFromFile (qid,fbTerms,fbDocs,fbMu,fbInitialRankingFile);
        		
        	}
        	else{
        		r = processQuery(query, model);
        		sortResults(r);
        		//Use this scorelist to generate the expanded query
        		expandedQry = generateExpanedQueryFromScoreList(fbTerms,fbDocs,fbMu,r);
        		
        	}
        	//use indri qry expansion to produce an expanded query

        	if(fbExpansionQueryFile!=null){
            	//write expanded qry to a file named by fbexpansionquery
        		
        		pwExandedQry.write(qid+": "+expandedQry+'\n');
        		//Be careful! Need to make sure next write does not overwrite the formal expanded query.
        				
        		//create a combined query
        		//weight is the original query weight
        		Double origWeight = Double.parseDouble(parameters.get("fbOrigWeight"));
        		
            	String combinedQuery = generateCombinedQuery(query,expandedQry,origWeight,model);
//            	System.out.println("combine: ");
//            	System.out.println(combinedQuery);
          		r = processQuery(combinedQuery, model);
        	}
        	else{
        		System.out.println("Error, please specify the file name of the expanded qyery");
        	}
        }
        

        if (r != null) {
        	
          sortResults(r);
          printResults(qid, r, parameters,output);  
          System.out.println();
        }
      }
      
      if(pwExandedQry!=null){
    	  pwExandedQry.close();
      }
      
    } catch (IOException ex) {
      ex.printStackTrace();
    } catch (Exception e) {
		// TODO Auto-generated catch block
    	System.out.println("get Internal Doc Id wrong!");
		e.printStackTrace();
	} finally {
      input.close();
      output.close();
    }
  }
  
static String generateExpanedQueryFromScoreList(int fbTerms,
		int fbDocs, int fbMu, ScoreList r) throws Exception {
	// TODO Auto-generated method stub
	
	//number of document to compute the weight for p(t|I)
	//generate the score list from the external file
	  
	ScoreList initialRanking = r;
	String expanedQry = "#wand( ";
	
	// in the while loop there are three part to be computed or get
	//p(t|d) = ()/()
	//Indri score for document in rank I, easy
	//log(length(c)/ctfd): idf
	//Nov 2nd
	
	//need to sort sort by the computed score for all term, then use the top fbTerms words
	Map<String, Double> termWeightMap = new HashMap<String,Double>();
	
	long corpusLength = Idx.getSumOfFieldLengths("body");
	
	//obtain the vocab that occur in the top n document
	
	Map<String, Double> termVocab = new HashMap<String,Double>();
	
	Map<Integer, Double> docIndriScoreDivideBodyLength = new HashMap<Integer,Double>();

	Map<String, Double> termCTF = new HashMap<String,Double>();
	
	Map<Integer,TermVector> docTermVector = new HashMap<Integer,TermVector>();

	
	for(int i=0;i < fbDocs;i++){
		
		int internalDocId = initialRanking.getDocid(i);
		double indriScore = initialRanking.getDocidScore(i);
		int docLength = Idx.getFieldLength("body",internalDocId);
		
		docIndriScoreDivideBodyLength.put(i, (indriScore)/(docLength+fbMu));
		
		TermVector bodyTermVector = new TermVector(internalDocId,"body");
		
		docTermVector.put(i, bodyTermVector);
		
		
		TermsEnum ithTerm = bodyTermVector.luceneTerms.iterator(null);
		
		
		for(int j = 1;  ithTerm.next() != null ;j++){
			
			String term = bodyTermVector.stemString(j);
			
			double ctf =  bodyTermVector.totalStemFreq(j);
			
			if(term.indexOf('.')==-1 && term.indexOf(',')==-1){
				if(!termVocab.containsKey(term)){
					termVocab.put(term, 1.0);
				}
				if(!termCTF.containsKey(term)){
					termCTF.put(term, ctf);
				}
			}
		}
	}
	
	
	for(Map.Entry<String, Double> currentTerm : termVocab.entrySet()){
		
		String term = currentTerm.getKey();
		
		for(int j=0;j<fbDocs;j++){
			
			double indriScoreDivideDocLength = docIndriScoreDivideBodyLength.get(j);
			
			TermVector bodyTermVector = docTermVector.get(j);
			//need to avoid using this, try to record a doc list that this term appear and use this list to determine whether exist 
			int idx = bodyTermVector.indexOfStem(term);
			
			double corpusTF = termCTF.get(term);
			
			if(idx!=-1){
				double termDocProb=0;
				
				double termWeight = 0;

				long tf = bodyTermVector.stemFreq(idx);
				
				
				double mleProb = corpusTF/corpusLength;
				
				termDocProb = (tf+fbMu*mleProb);
				
				double idf = Math.log(corpusLength/corpusTF);
				
				termWeight += termDocProb*indriScoreDivideDocLength*idf;
				
				if(termWeightMap.containsKey(term)){
					double formerWeight = termWeightMap.get(term);
					termWeightMap.put(term, (termWeight+formerWeight));
				}else{
					termWeightMap.put(term, termWeight);
				}
				
			}
			else{
				// compute default score
				
				double termWeight = 0;
				
				double mleProb = corpusTF/corpusLength;
				
				double termDocProb = fbMu*mleProb;
				
				double idf = Math.log(corpusLength/corpusTF);
				
				termWeight += termDocProb*indriScoreDivideDocLength*idf;
				
				if(termWeightMap.containsKey(term)){
					double formerWeight = termWeightMap.get(term);
					termWeightMap.put(term, (termWeight+formerWeight));
				}else{
					termWeightMap.put(term, termWeight);
				}
			}
		}
	}
	
	List<Map.Entry<String, Double>> infoIds = new ArrayList<Map.Entry<String, Double>>(termWeightMap.entrySet());
	
	Collections.sort(infoIds, new Comparator<Map.Entry<String, Double>>() {   
	    public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {      
	        return o2.getValue().compareTo(o1.getValue()); 
	    }
	}); 
	
	for(int j = fbTerms-1;j > -1; j--){
		
		String tm =  infoIds.get(j).getKey();
		double tw =  infoIds.get(j).getValue();
		//tw = (double)Math.round(tw * 10000d) / 10000d;
		String twString  = String.format("%.4f", tw);
		//System.out.println("term: "+tm+" weight: "+twString);
		
		expanedQry +=twString+" "+tm+" ";
	}
	
	expanedQry += " )";
	
	//Runtime.getRuntime().gc();
	
	return expanedQry;
	
}

private static String generateCombinedQuery(String query, String expandedQry,
		Double origWeight,RetrievalModel r) throws IOException {
	//Qry q = parseQuery(query,r);
	String defaultSopName = r.defaultQrySopName();
	return "#wand( "+origWeight+" "+defaultSopName+" ( "+ query +" ) "+ (1-origWeight)+" "+expandedQry+" )";
	
}

static String generateExpanedQueryFromFile (String qid,int fbTerms,int fbDocs,int fbMu,String fbInitialRankingFile) throws Exception{
	//number of document to compute the weight for p(t|I)
	//generate the score list from the external file	  
	ScoreList initialRanking = readInitialRankingFile(qid,fbDocs,fbInitialRankingFile);
	
	return generateExpanedQueryFromScoreList(fbTerms, fbDocs, fbMu, initialRanking);

  }
  
  // Sep 15 first by score descending
  // second by external doc id ascending
  static void sortResults (ScoreList result) throws IOException {
	  if (result == null){
			throw new IOException();
	  }
	  result.sort();
  }
  /**
   * Print the query results.
   * 
   * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
   * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
   * 
   * QueryID Q0 DocID Rank Score RunID
   * 
   * @param queryName
   *          Original query.
   * @param result
   *          A list of document ids and scores
   * @throws IOException Error accessing the Lucene index.
   */
  
  // add sort and change output format
  // 10 Q0 clueweb09-en0000-00-03431 1 1.000000000000 fubar
  //dummy output: 10	Q0	dummy	1	0	run-1
  static void printResults(String queryName, ScoreList result, Map<String,String> parameters, PrintWriter output) throws IOException {
	
	if(result == null){
		throw new IOException();
	}

    if (result.size() < 1) {
      output.println(queryName + "\tQ0\tdummy\t1\t0\trun-1");
     // System.out.println(queryName + "\tQ0\tdummy\t1\t0\trun-1");
      
    }else {
      for (int i = 0; i < result.size() && i < 100; i++) {
    	int rank = i+1;
    	output.println(queryName + "\tQ0\t"+Idx.getExternalDocid(result.getDocid(i))+"\t"+rank+"\t"+String.format("%.16f", result.getDocidScore(i))+"\trun-1"); 
    	System.out.println(queryName + "\tQ0\t"+Idx.getExternalDocid(result.getDocid(i))+"\t"+rank+"\t"+String.format("%.12f", result.getDocidScore(i))+"\trun-1"); 
        
      }
      
    }
  }
  

  /**
   * Read the specified parameter file, and confirm that the required
   * parameters are present.  The parameters are returned in a
   * HashMap.  The caller (or its minions) are responsible for
   * processing them.
   * @return The parameters, in <key, value> format.
   */
  static Map<String, String> readParameterFile (String parameterFileName)
    throws IOException {

    Map<String, String> parameters = new HashMap<String, String>();

    File parameterFile = new File (parameterFileName);

    if (! parameterFile.canRead ()) {
      throw new IllegalArgumentException
        ("Can't read " + parameterFileName);
    }

    Scanner scan = new Scanner(parameterFile);
    String line = null;
    do {
      line = scan.nextLine();
      String[] pair = line.split ("=");
      parameters.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());

    scan.close();

    if (! (parameters.containsKey ("indexPath") &&
           parameters.containsKey ("queryFilePath") &&
           parameters.containsKey ("trecEvalOutputPath") &&
           parameters.containsKey ("retrievalAlgorithm")||
           parameters.containsKey("BM25:k_1")||
           parameters.containsKey("BM25:b")||
           parameters.containsKey("BM25:k_3")||
           parameters.containsKey("Indri:mu")||
           parameters.containsKey("Indri:lambda")||
           parameters.containsKey("fb")||
           parameters.containsKey("fbDocs")||
           parameters.containsKey("fbTerms")||
           parameters.containsKey("fbMu")||
           parameters.containsKey("fbOrigWeight")||
           parameters.containsKey("fbInitialRankingFile")||
           parameters.containsKey("fbExpansionQueryFile")||
           parameters.containsKey("letor:trainingQueryFile")||
           parameters.containsKey("letor:trainingQrelsFile")||
           parameters.containsKey("letor:trainingFeatureVectorsFile")||
           parameters.containsKey("letor:pageRankFile")||
           parameters.containsKey("letor:featureDisable")||
           parameters.containsKey("letor:svmRankLearnPath")||
           parameters.containsKey("letor:svmRankClassifyPath")||
           parameters.containsKey("letor:svmRankParamC")||
           parameters.containsKey("letor:svmRankModelFile")||
           parameters.containsKey("letor:testingFeatureVectorsFile")||
           parameters.containsKey("letor:testingDocumentScores")
           )) {
      throw new IllegalArgumentException
        ("Required parameters were missing from the parameter file.");
    }

    return parameters;
  }
  
  private static  ScoreList readInitialRankingFile(String qid,int fbDocs,String fbInitialRankingFile)  throws Exception{
	    
	    File rankingFile = new File (fbInitialRankingFile);
	    
	    ScoreList retrievalDocScore = new ScoreList();
	    
	    if (! rankingFile.canRead ()) {
	      throw new IllegalArgumentException
	        ("Can't read " + fbInitialRankingFile);
	    }

	    Scanner scan = new Scanner(rankingFile);
	    
	    String line = null;
	    
	    int lineNum = 0;
	    
	    do {
	      line = scan.nextLine();
	    
	      String[] pair = line.split ("\\s");
	      
	      //Nov 4, deal with multiple query output in one ranking file, need to test !!!!!
	      if(pair[0].compareTo(qid) == 0){
	    	//  System.out.println(line);
		      // may not exist such doc
		      int internalDocId = Idx.getInternalDocid(pair[2].trim());
		      double IndriScore = Double.parseDouble(pair[4].trim());
		      //wrong external --> internal --> external
		      retrievalDocScore.add(internalDocId, IndriScore);
		      lineNum ++; 
	      }	      
	    } while (scan.hasNext()&&lineNum < fbDocs);

	    scan.close();
	    return retrievalDocScore;
	  
  }

  /**
   * Given a query string, returns the terms one at a time with stopwords
   * removed and the terms stemmed using the Krovetz stemmer.
   * 
   * Use this method to process raw query terms.
   * 
   * @param query
   *          String containing query
   * @return Array of query tokens
   * @throws IOException Error accessing the Lucene index.
   */
  static String[] tokenizeQuery(String query) throws IOException {

    TokenStreamComponents comp =
      ANALYZER.createComponents("dummy", new StringReader(query));
    TokenStream tokenStream = comp.getTokenStream();

    CharTermAttribute charTermAttribute =
      tokenStream.addAttribute(CharTermAttribute.class);
    tokenStream.reset();

    List<String> tokens = new ArrayList<String>();

    while (tokenStream.incrementToken()) {
      String term = charTermAttribute.toString();
      tokens.add(term);
    }

    return tokens.toArray (new String[tokens.size()]);
  }
  

}
