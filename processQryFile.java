import java.util.ArrayList;


public class processQryFile {
	
	public static void main(String[] args){
		
		/*10:#AND( #WSUM(0.1 cheap.url  0.0 cheap.keywords 0.2 cheap.title      0.3 cheap.inlink      0.4 cheap.body) #WSUM(0.1 internet.url  0.0 internet.keywords 0.2 internet.title      0.3 internet.inlink      0.4 internet.body))
12:#AND( #WSUM(0.1 djs.url  0.0 djs.keywords 0.2 djs.title      0.3 djs.inlink      0.4 djs.body))
26:#AND( #WSUM(0.1 lower.url  0.0 lower.keywords 0.2 lower.title      0.3 lower.inlink      0.4 lower.body) #WSUM(0.1 heart.url  0.0 heart.keywords 0.2 heart.title      0.3 heart.inlink      0.4 heart.body) #WSUM(0.1 rate.url  0.0 rate.keywords 0.2 rate.title      0.3 rate.inlink      0.4 rate.body))
29:#AND( #WSUM(0.1 ps.url  0.0 ps.keywords 0.2 ps.title      0.3 ps.inlink      0.4 ps.body) #WSUM(0.1 2.url  0.0 2.keywords 0.2 2.title      0.3 2.inlink      0.4 2.body) #WSUM(0.1 games.url  0.0 games.keywords 0.2 games.title      0.3 games.inlink      0.4 games.body))
33:#AND( #WSUM(0.1 elliptical.url  0.0 elliptical.keywords 0.2 elliptical.title      0.3 elliptical.inlink      0.4 elliptical.body) #WSUM(0.1 trainer.url  0.0 trainer.keywords 0.2 trainer.title      0.3 trainer.inlink      0.4 trainer.body))
52:#AND( #WSUM(0.1 avp.url  0.0 avp.keywords 0.2 avp.title      0.3 avp.inlink      0.4 avp.body))
71:#AND( #WSUM(0.1 living.url  0.0 living.keywords 0.2 living.title      0.3 living.inlink      0.4 living.body) #WSUM(0.1 in.url  0.0 in.keywords 0.2 in.title      0.3 in.inlink      0.4 in.body) #WSUM(0.1 india.url  0.0 india.keywords 0.2 india.title      0.3 india.inlink      0.4 india.body))
102:#AND( #WSUM(0.1 fickle.url  0.0 fickle.keywords 0.2 fickle.title      0.3 fickle.inlink      0.4 fickle.body) #WSUM(0.1 creek.url  0.0 creek.keywords 0.2 creek.title      0.3 creek.inlink      0.4 creek.body) #WSUM(0.1 farm.url  0.0 farm.keywords 0.2 farm.title      0.3 farm.inlink      0.4 farm.body))
149:#AND( #WSUM(0.1 uplift.url  0.0 uplift.keywords 0.2 uplift.title      0.3 uplift.inlink      0.4 uplift.body) #WSUM(0.1 at.url  0.0 at.keywords 0.2 at.title      0.3 at.inlink      0.4 at.body) #WSUM(0.1 yellowstone.url  0.0 yellowstone.keywords 0.2 yellowstone.title      0.3 yellowstone.inlink      0.4 yellowstone.body) #WSUM(0.1 national.url  0.0 national.keywords 0.2 national.title      0.3 national.inlink      0.4 national.body) #WSUM(0.1 park.url  0.0 park.keywords 0.2 park.title      0.3 park.inlink      0.4 park.body))
190:#AND( #WSUM(0.1 brooks.url  0.0 brooks.keywords 0.2 brooks.title      0.3 brooks.inlink      0.4 brooks.body) #WSUM(0.1 brothers.url  0.0 brothers.keywords 0.2 brothers.title      0.3 brothers.inlink      0.4 brothers.body) #WSUM(0.1 clearance.url  0.0 clearance.keywords 0.2 clearance.title      0.3 clearance.inlink      0.4 clearance.body))
		 * */
		String urlWeight = "0.15";
		String keywordsWeight = "0.0";
		String titleWeight = "0.0";
		String inlinkWeigtht = "0.00";
		String bodyWeight = "0.85";
		
		String[] qry1 = {"cheap","internet"};
		String[] qry2 = {"djs"};
		String[] qry3 = {"lower","heart","rate"};
		String[] qry4 = {"ps","2","games"};
		String[] qry5 = {"elliptical","trainer"};
		String[] qry6 = {"avp"};
		String[] qry7 = {"living","in","india"};
		String[] qry8 = {"fickle","creek","farm"};
		String[] qry9 = {"uplift","at","yellowstone","national","park"};
		String[] qry10= {"brooks","brothers","clearance"};
		
		ArrayList<String[]> list =  new ArrayList<String[]>();
		
		list.add(qry1);
		list.add(qry2);
		list.add(qry3);
		list.add(qry4);
		list.add(qry5);
		list.add(qry6);
		list.add(qry7);
		list.add(qry8);
		list.add(qry9);
		list.add(qry10);
		
		int[] qryNum={10,12,26,29,33,52,71,102,149,190};
		
		
		String qry = "";

		for(int i = 0; i< 10;i++){
			String[] words = list.get(i);
			String qryline="";
			for(String word1 : words){
				String q1 = " #WSUM("+urlWeight+" "+word1+".url "+keywordsWeight+" "+word1+".keywords "+titleWeight+" "+word1+".title "+inlinkWeigtht+" "+word1+".inlink "+bodyWeight+" "+word1+".body)";
				qryline +=q1;
			}
			qry=qryNum[i]+":#AND("+qryline+")";
			System.out.println(qry);
		}
		
	}

}
