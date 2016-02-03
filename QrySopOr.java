/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopOr extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
	//  System.out.println("inQrysopOr: "+this.getClass());
	  return this.docIteratorHasMatchMin (r);
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
    }
    if (r instanceof RetrievalModelRankedBoolean ){
    	return this.getScoreRankedBoolean(r);
    }
    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the OR operator.");
    }
  }
  
  /**
   *  getScore for the UnrankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return 1.0;
    }
  }
    
  private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
    	double score = 0;
    	
    	// for pter with same docId = minDocid
    	
    	int minDocid = this.docIteratorGetMatch();
    	
    	for (int i=0;i< this.args.size();i++){
    		
    		if(((QrySop)(this.args.get(i))).docIteratorHasMatch(r)){
	    		int docid = ((QrySop)(this.args.get(i))).docIteratorGetMatch();
	    		
	    		if (docid == minDocid){
	    			double tempScore = ((QrySop)(this.args.get(i))).getScore(r);
	    			if(tempScore > score){
	    				score = tempScore;
	    			}
	    		}
    		}
    	}
		return score;
    }

 }

@Override
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
		if(r instanceof RetrievalModelIndri){
			score = score*(1-tempScore);
		}
	}
	score = 1-score;
	return score;
}
}
