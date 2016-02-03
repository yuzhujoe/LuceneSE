/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

/**
 *  The sum operator for all retrieval models.
 */
public class QrySopWsum extends QrySop {
	
  private ArrayList<Double> QryWeight = new ArrayList<Double>();
  
  /**
   * 
   * @param weight
   */
  public void addWeight(double weight){
	  this.QryWeight.add(weight);
  }
  
  /**
   *  normalize weight
   */
  public void normailzeWeight(){
	  double sumWeight = 0;
	  for(Double d : QryWeight){
		  sumWeight +=d; 
	  }
	  for(int i = 0;i < QryWeight.size();i++){
		  QryWeight.set(i, QryWeight.get(i)/sumWeight);
	  }
  }
  
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

	
    if (r instanceof RetrievalModelIndri) {
      return this.getScoreIndri (r);
    }
    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the AND operator.");
    }
  }
  

  //Oct 20
  private double getScoreIndri(RetrievalModel r) throws IOException {
	// normalize weight Oct 22
	normailzeWeight();
	
	double score = 0;
	
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
		// this is the diff from wand !!!! but wsum can only be used to same word for diff field?
		score = score + tempScore*QryWeight.get(i);
		
	}
	return score;
  }

  public double getDefaultScore(RetrievalModel r, int targetDocId) throws IOException {
	normailzeWeight();
	
	double score = 0;
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
			score = score + tempScore*QryWeight.get(i);
		}
	}
	
	return score;
}
  @Override public String toString(){
	    
	    String result = new String ();

	    for (int i=0; i<this.args.size(); i++)
	      result += this.QryWeight.get(i) +" "+ this.args.get(i) + " ";

	    return (this.displayName + "( " + result + ")");
  }

}
