package java.duplication;


public class HelloWorld {

	public static void main(String[] args) {
		System.out.println("Hi, I ran successfully");
		System.exit(-255);
	}
	
	public double doCalc(){
		
		//Some duplication
		//Some duplication
		//Some duplication
		double a = 100;
		a++;
		a--;
		Math.sqrt(a);		
		
		return a;		
	}
}
