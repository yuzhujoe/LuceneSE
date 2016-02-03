/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.Arrays;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopAnd extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
	  //all 0 then not match otherwise has match, so take min
	  if(r instanceof RetrievalModelIndri){
		  return this.docIteratorHasMatchMin (r);
	  }else{
		  return this.docIteratorHasMatchAll (r);
	  }
	  
  }
  

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  
  //Sep 12
  public double getScore (RetrievalModel r) throws IOException {
    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean (r);
    }else if (r instanceof RetrievalModelRankedBoolean ){
    	return this.getScoreRankedBoolean(r);
    }else if(r instanceof RetrievalModelIndri){
    	return this.getScoreIndri(r);
    }else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the AND operator.");
    }
  }
  
  /**
   *  getScore for the UnrankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (this.docIteratorHasMatchCache()) {
      return 1.0;
    } else {
      return 0.0;
    }
  }
  
  //Sep 12
  private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
    	
    	double score = -1;
    	
    	int minDocid = this.docIteratorGetMatch();
    	
    	for (int i=0;i< this.args.size();i++){
    		
    		if(((QrySop)(this.args.get(i))).docIteratorHasMatch(r)){
	    		int docid = ((QrySop)(this.args.get(i))).docIteratorGetMatch();
	    		
	    		if (docid == minDocid){
	    			double tempScore = ((QrySop)(this.args.get(i))).getScore(r);
	    			if(tempScore < score|| score == -1){
	    				score = tempScore;
	    			}
	    		}
    		}
    	}
	  return score;
    }
  }
  
  private double getScoreIndri(RetrievalModel r) throws IOException {
	double score = 1;
	
	int minDocid =  this.docIteratorGetMatch();
	int querySize = this.args.size();	
	
	for (int i=0;i< querySize;i++){
		double tempScore;
		if(((QrySop)(this.args.get(i))).docIteratorHasMatch(r)){
    		int docid = ((QrySop)(this.args.get(i))).docIteratorGetMatch();
    		
			if(docid == minDocid ){
				tempScore = ((QrySop)(this.args.get(i))).getScore(r);
			}else{
				tempScore = ((QrySop)(this.args.get(i))).getDefaultScore(r,minDocid);
			}
		}
		else{
			tempScore = ((QrySop)(this.args.get(i))).getDefaultScore(r,minDocid);
		}
		score = score * tempScore;
	}
	score = Math.pow(score, ((double)1)/querySize);
	return score;
  }

  //no match for this query but still want the score
  public double getDefaultScore(RetrievalModel r, int targetDocId) throws IOException {
	double score = 1;
	double tempScore;
	
	int docid = Qry.INVALID_DOCID;
	int querySize = this.args.size();	
	
	for (int i=0;i< querySize;i++){
		if(((QrySop)(this.args.get(i))).docIteratorHasMatch(r)){
    		docid = ((QrySop)(this.args.get(i))).docIteratorGetMatch();
		}
		
		if( docid == targetDocId){
			tempScore = ((QrySop)(this.args.get(i))).getScore(r);
		}else{
			tempScore = ((QrySop)(this.args.get(i))).getDefaultScore(r,targetDocId);
		}
		score = score* tempScore;
	}
	score = Math.pow(score, ((double)1)/querySize);
		
	return score;
  }
}
