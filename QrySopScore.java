/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */
  
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchFirst (r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {
    if (r instanceof RetrievalModelUnrankedBoolean) {
    	return this.getScoreUnrankedBoolean (r);
    }else if(r instanceof RetrievalModelRankedBoolean){
    	return this.getScoreRankedBoolean(r);
    }else if(r instanceof RetrievalModelBM25){
    	return this.getScoreBM25(r);
    }else if(r instanceof RetrievalModelIndri){
    	return this.getScoreIndri(r);
    }
    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }
  
  public double getDefaultScore (RetrievalModel r,int docid) throws IOException {
    if(r instanceof RetrievalModelIndri){
    	return this.getDefaultScoreIndri(r,docid);
    }
    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }
  




/**
   *  getScore for the Unranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return 1.0;
    }
  }
  
  public double getScoreRankedBoolean (RetrievalModel r) throws IOException {
	    if (! this.docIteratorHasMatchCache()) {
	      return 0.0;
	    } else {	
	    	//System.out.println((this.getArg(0)).docIteratorGetMatchPosting().tf);
	    	return (this.getArg(0)).docIteratorGetMatchPosting().tf;
	    }
  }
  
  public double getScoreBM25 (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {	
    	//System.out.println((this.getArg(0)).docIteratorGetMatchPosting().tf);
    	//follow the formula
    	long N = Idx.getNumDocs();
    	int df = (this.getArg(0)).getDf ();
    	String field = (this.getArg(0)).getField();
    	//long N = Idx.getDocCount(field);
    	int tftd =  (this.getArg(0)).docIteratorGetMatchPosting().tf;
    	
    	int internalDocId = (this.getArg(0)).docIteratorGetMatchPosting().docid;
    	
    	//need to get field from outside
    	int docLength = Idx.getFieldLength(field,internalDocId);

    	double avgDocLength =  (double)(Idx.getSumOfFieldLengths(field)) / (double)(Idx.getDocCount (field));
    	
    	double k_1 = ((RetrievalModelBM25)r).getk_1();
    	double k_3 = ((RetrievalModelBM25)r).getk_3();
    	//int k_3 = 1;
    	double b = ((RetrievalModelBM25)r).getb();
    	
    	double score = Math.max(0.0,Math.log((N-df+0.5)/(df+0.5)))*((double)tftd)/(((double)tftd)+k_1*((1.0-b)+b*((double)docLength)/((double)avgDocLength)));
    	return score;
    }
  }
  
  public double getScoreIndri(RetrievalModel r) throws IOException {
	
	if (! this.docIteratorHasMatchCache()) {
	  return 0.0;  
	}
	
	double score;  
	
	double mu = ((RetrievalModelIndri)r).getMu();
	double lambda = ((RetrievalModelIndri)r).getLambda();
	
	int	tftd = (this.getArg(0)).docIteratorGetMatchPosting().tf;
	
	String field = (this.getArg(0)).getField();
	int internalDocId = (this.getArg(0)).docIteratorGetMatchPosting().docid;

	int docLength = Idx.getFieldLength(field,internalDocId);
	
	long corpusLength = Idx.getSumOfFieldLengths(field);
	int corpusTF = (this.getArg(0)).getCtf();
	double mleProb = (corpusTF*1.0)/corpusLength;
	score = (1-lambda)*(tftd+mu*mleProb)/(docLength+mu)+lambda*mleProb;
	
	return score;
  }
    
  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize (RetrievalModel r) throws IOException {

    Qry q = this.args.get (0);
    q.initialize (r);
  }


public double getDefaultScoreIndri(RetrievalModel r,int docid) throws IOException {
	// TODO Auto-generated method stub
	double score;  
	
	double mu = ((RetrievalModelIndri)r).getMu();
	double lambda = ((RetrievalModelIndri)r).getLambda();
	
	int tftd = 0;
	
	String field = (this.getArg(0)).getField();
	int internalDocId = docid;
	
	int docLength = Idx.getFieldLength(field,internalDocId);
	
	long corpusLength = Idx.getSumOfFieldLengths(field);
	int corpusTF = (this.getArg(0)).getCtf();
	double mleProb = (corpusTF*1.0)/corpusLength;
	
	score = (1-lambda)*(tftd+mu*mleProb)/(docLength+mu)+lambda*mleProb;
	return score;
}

}
