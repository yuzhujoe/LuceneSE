

/**
 *  An object that stores parameters for the bm25
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelBM25 extends RetrievalModel {
	
  private double k_1;
  private double k_3;
  private double b;
  
	
  public RetrievalModelBM25(double k_1,double k_3,double b){
	  this.k_1 = k_1;
	  this.k_3 = k_3;
	  this.b = b;
  }
  
  public String defaultQrySopName () {
    return new String ("#sum");
  }
  
  public double getk_1(){
	  return this.k_1;
  }
  
  public double getk_3(){
	  return this.k_3;
  }
  
  public double getb(){
	  return this.b;
  }
}
