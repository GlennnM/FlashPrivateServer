import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
/**
 *	the procedure 
 *  1. get map.bin and scripts.csv from ninja kiwi
 *  http://assets.nkstatic.com/Games/gameswfs/sas4/download/scripts.csv
 *  (example map: sea lab)
 *  http://assets.nkstatic.com/Games/gameswfs/sas4/download/maps/1119.bin?v=1376
 *  then replace the paths in the code
 *  2. take the short value after COntractMasterControllerData or whatever, find its index in table 4
 *  3. get the values there and put them in searchTable(change the loop) - this will search table 2 for those id's
 *  4. itll generate table 1 entries(with same coords), add them
 *  5. add teleport thing with id's(3 of them in the places where it reads a short) random table 4 entries
 *  6. add alphavirusdata thing with table 1 entries(the generated ones, up to 10 short vals)
 *  7. update the lengths of tables 1 and 11, make sure it parse
 *  8. ???
 *  9. hydar
 * */
class Entry1{
	int id;float x; float y;
	public Entry1(int id, float x, float y) {
		this.id=id;
		this.x=x;
		this.y=y;
	}
}
public class ParseMap{
	public static String getScriptName(int id){
		try {
			List<String> lines = Files.readAllLines(Paths.get("PATH_TO_SCRIPTS.csv"),StandardCharsets.UTF_8);
			for(String line:lines) {
				if(line.startsWith(""+id))
					return line.substring(line.indexOf(','),line.indexOf(',')+1+line.substring(line.indexOf(',')+1).indexOf(','));
			
			}
		}catch(Exception e) {
		e.printStackTrace();
		}
		return "UNKNOWN";
	}
	public static void main(String[] hydar){
		
		try{
			FileInputStream x = new FileInputStream("PATH_TO_map.bin");
			DataInputStream n = new DataInputStream(x);
			int id = n.readShort();
			System.out.println("Map ID: "+id);
			int _loc10_ = n.readShort();
			System.out.println("Scan 1: "+_loc10_+" items");
			int _loc20_ = 0;
			String[] secondaryCodes= new String[] {"78","65","6e","6f","76","69","61","e0","e1","e2"};
			//Integer[] searchTable = new Integer[] {366,367,368,369,370,371,372,373,374,375};
			Integer[] searchTable = new Integer[10];
			for(int i=0;i<searchTable.length;i++)
				searchTable[i]=231+i;
			List<Integer> searchList = Arrays.asList(searchTable);
			ArrayList<Entry1> table1 = new ArrayList<Entry1>();
			 while(_loc20_ < _loc10_)
			 {
				int id1=n.readShort();
				n.readShort();
				n.readFloat();
				n.readBoolean();
				n.readBoolean();
				float x1=n.readFloat();
				float y1=n.readFloat();
				table1.add(new Entry1(id1,x1,y1));
				n.readFloat();
				n.readFloat();
				n.readFloat();
				n.readBoolean();
				n.readBoolean();
				n.readShort();
				n.readShort();
				_loc20_++;
			 }
			System.out.println("Available incl. next: " +n.available());
			 int _loc9_ = n.readShort();
				System.out.println("Scan 2: "+_loc9_+" items");
			 _loc20_ = 0;
				int index=0;
			 while(_loc20_ < _loc9_)
			 {
				int t2id = n.readShort();
				float x2 =n.readFloat();
				float y2 =n.readFloat();
				if(searchList.contains(t2id)) {
					String hexId=Integer.toHexString((t2id&0xff));
					hexId=hexId.toUpperCase();
					System.out.println("20"+secondaryCodes[index].toUpperCase()+" 0050 00000000 0000 "+Integer.toHexString(Float.floatToIntBits(x2)).toUpperCase()+" "+Integer.toHexString(Float.floatToIntBits(y2)).toUpperCase()+" 00000000 3F800000 3F800000 0000 0000 0000");
					
					index++;
					/**System.out.println("Table 1 matches:");
					for(Entry1 e:table1) {
						if(Math.abs(e.x-x2)<2f&&Math.abs(e.y-y2)<2f) {
							System.out.println(e.id);
						}
					}*/
				}
				n.readUTF();
				_loc20_++;
			 }
			 int _loc19_ = n.readShort();
				System.out.println("Scan 3: "+_loc19_+" items");
			 _loc20_ = 0;
			 while(_loc20_ < _loc19_)
			 {
				n.readShort();
				n.readShort();
				n.readFloat();
				_loc20_++;
			 }
			 System.out.println("Available incl. next: " +n.available());
			 int _loc27_ = n.readShort();
				System.out.println("Scan 4: "+_loc27_+" items");
			 _loc20_ = 0;
			 ArrayList<Integer> table4 = new ArrayList<Integer>();
			 ArrayList<Integer> table4_off = new ArrayList<Integer>();
			 while(_loc20_ < _loc27_)
			 {
				table4_off.add(n.available());
				table4.add((int)n.readShort());
				_loc9_=n.readShort();
				int _loc13_ = 0;
				while(_loc13_ < _loc9_)
				{
				   n.readShort();
				   _loc13_++;
				}
				_loc20_++;
			 }System.out.println(table4);
			 System.out.println("at offsets from end:");
			 System.out.println(table4_off);
			 int _loc3_ = n.readShort();
				System.out.println("Scan 5: "+_loc3_+" items");
			 _loc20_ = 0;
			 while(_loc20_ < _loc3_)
			 {
				n.readShort();
				_loc20_++;
			 }
			 int loc17 =n.readShort();
				System.out.println("Scan 6: "+loc17+" items");
			 _loc20_ = 0;
			 while(_loc20_ < loc17)
			 {
				n.readShort();
				_loc20_++;
			 }
			 int _loc18_ =n.readShort();
				System.out.println("Scan 7: "+_loc18_+" items");
			 _loc20_ = 0;
			 while(_loc20_ < _loc18_)
			 {
				n.readShort();
				n.readFloat();
				n.readFloat();
				_loc20_++;
			 }
			 int _loc31_ = n.readShort();
				System.out.println("Scan 8: "+_loc31_+" items");
			 _loc20_ = 0;
			 while(_loc20_ < _loc31_)
			 {
				int q=n.readShort();
				float r=n.readFloat();
				float t=n.readFloat();
				int a=n.readShort();
				if(a != 0)
				{
				   int _loc14_=n.readShort();
				   byte[] data = new byte[_loc14_];
				   int _loc8_ = 0;
				   while(_loc8_ < _loc14_)
				   {
					  data[_loc8_]=n.readByte();
					  _loc8_++;
				   }
				   System.out.println("id: "+q+"("+r+","+t+") script: "+a+"\n"+Arrays.asList(data));
				}else{
				   System.out.println("id: "+q+"("+r+","+t+") script: "+a+"\n"+"N/a");
					
					
				}
				
			 }
			 int _loc6_ = n.readShort();
				System.out.println("Scan 9: "+_loc6_+" items");
	         _loc20_ = 0;
	         while(_loc20_ < _loc6_)
	         {
	            n.readShort();
	            n.readShort();
	            n.readShort();
	            n.readShort();
	            n.readShort();
	            _loc20_++;
	         }
	         int _loc4_ = n.readShort();
				System.out.println("Scan 10: "+_loc4_+" items");
	         _loc20_ = 0;
	         while(_loc20_ < _loc4_)
	         {
	           n.readShort();
	            n.readFloat();
	            n.readFloat();
	            _loc20_++;
	         }
			 System.out.println("Available including length: "+n.available());
	         int _loc12_ = n.readShort();
			 System.out.println("length: "+_loc12_+"\n==========================");
	         _loc20_ = 0;
	         ArrayList<Integer> uniques = new ArrayList<Integer>();
	         while(_loc20_ < _loc12_)
	         {
	            int a_ =n.readShort();
	            float x_=n.readFloat();
	            float y_=n.readFloat();
	            int e=n.readShort();
	            System.out.println("Script "+e+getScriptName(e));
	            System.out.println(a_+" ("+x_+","+y_+")");
	            if(!uniques.contains(e))
	            	uniques.add(e);
	            if(e != 0)
	            {
	               int _loc8_ = 0;
	               int _loc14_=	   n.readShort();
		              byte[] data = new byte[_loc14_];
	               while(_loc8_ < _loc14_)
	               {
	                  data[_loc8_]=n.readByte();
	                  _loc8_++;
	               }
	               System.out.println(data.length+":"+Arrays.toString(data));
	            }
	            _loc20_++;
	         }System.out.println("Unique: "+uniques.size());
	         System.out.println("Available including next length: "+n.available());
	         int _loc15_ = 0;
			 System.out.println("length: "+_loc15_+"\n==========================");
	         _loc15_ = n.readByte();
	         _loc20_ = 0;
	         while(_loc20_ < _loc15_)
	         {
	        	 _loc6_ = 0;
		         //System.out.println("TYPE: "+
	        	 n.readShort();
		        // );
		        // System.out.println("str: "+
		         n.readUTF();
		        // );
		        // if(o1978 == null)
		       //  {
		       //     o1978 = "NOT SET";
		      //   }
		         int _loc5_ = n.readShort();
		         _loc6_ = 0;
		         while(_loc6_ < _loc5_)
		         {
		            n.readShort();
		            _loc6_++;
		         }
		         int _loc2_ = n.readShort();
		         _loc6_ = 0;
		         while(_loc6_ < _loc2_)
		         {
		            n.readShort();
		            _loc6_++;
		         }
		         _loc9_ = n.readShort();
		         _loc6_ = 0;
		         while(_loc6_ < _loc9_)
		         {
		            n.readShort();
		            _loc6_++;
		         }
		         _loc4_ = n.readShort();
		         _loc6_ = 0;
		         while(_loc6_ < _loc4_)
		         {
		            n.readShort();
		            _loc6_++;
		         }
		         //o7642 = new Vector.<int>();
		         int _loc7_ = n.readShort();
		         _loc6_ = 0;
		         while(_loc6_ < _loc7_)
		         {
		           n.readShort();
		            _loc6_++;
		         }
		         _loc10_ =n.readShort();
		         _loc6_ = 0;
		         while(_loc6_ < _loc10_)
		         {
		            n.readShort();
		            _loc6_++;
		         }
		        _loc3_ = n.readShort();
		         _loc6_ = 0;
		         while(_loc6_ < _loc3_)
		         {
		            n.readShort();
		            _loc6_++;
		         }
		         int _loc8_ = n.readShort();
		         _loc6_ = 0;
		         while(_loc6_ < _loc8_)
		         {
		            n.readShort();
		            _loc6_++;
		         }
	            _loc20_++;
	         }
			 
			 
			 
			n.close();
			x.close();
		}catch(IOException e){e.printStackTrace();return;}
	}
	
}