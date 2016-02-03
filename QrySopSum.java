/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.Arrays;

/**
 *  The sum operator for all retrieval models.
 */
public class QrySopSum extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
	  //all 0 then not match otherwise has match, so take min
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
    if (r instanceof RetrievalModelBM25) {
      return this.getScoreBM25 (r);
    }
    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the AND operator.");
    }
  }
  
  /**
   *  getScore for the BM25 retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */

  
  //Oct 4
  private double getScoreBM25 (RetrievalModel r) throws IOException {
	if (! this.docIteratorHasMatchCache()) {
	    return 0.0;
	  } else {
	  	double score = 0;
	  	
	  	int minDocid = this.docIteratorGetMatch();
	  	
	  	for (int i=0;i< this.args.size();i++){
	  		if(((QrySop)(this.args.get(i))).docIteratorHasMatch(r)){
	    		int docid = ((QrySop)(this.args.get(i))).docIteratorGetMatch();
	    		
	    		if (docid == minDocid){
	    			double tempScore = ((QrySop)(this.args.get(i))).getScore(r);
	    				score += tempScore;
	    		}
	  		}
	  	}
	  	
		return score;
	  }
  }

public double getDefaultScore(RetrievalModel r, int docid) throws IOException {
    if (r instanceof RetrievalModelBM25) {
        return 0;
      }
    else{
        throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the SUM operator.");
    }
}
}
