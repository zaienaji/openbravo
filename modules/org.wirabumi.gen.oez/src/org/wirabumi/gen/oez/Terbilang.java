package org.wirabumi.gen.oez;


public class Terbilang {
	  static String bil[] = new String[] { "", "Satu ", "Dua ", "Tiga ", "Empat ", "Lima ", "Enam ", "Tujuh ", "Delapan ", "Sembilan ", "Sepuluh ", "Sebelas " };
	  StringBuffer stringBuff = new StringBuffer();
	  
	  public String generateTerbilangWithCurrency(double number, String currency){
		  
		  //check apakah ada desimal
		  long whole = (long) number;
		  long fraction = Math.round((number-whole)*100);
		  
		  Terbilang wholeTerbilang = new Terbilang();
		  Terbilang fractionTerbilang = new Terbilang();
		  String stringWholeTerbilang = wholeTerbilang.generate(whole);
		  String stringFractionTerbilang; 
		  
		  if (fraction!=0.00){
			  wholeTerbilang = new Terbilang();
			  fractionTerbilang = new Terbilang();
			  stringFractionTerbilang = fractionTerbilang.generate(fraction);
			  
			  return stringWholeTerbilang+currency+" "+stringFractionTerbilang+"Sen.";
			
		  } else {
			  return stringWholeTerbilang+currency;

		  }

	  }

	  public String generate(double number) {
		  
	    if(number < 0) {
	      stringBuff.append("Minus ");
	      generate(number * -1);
	    }
	    if (number < 12&& number>0) {
	      stringBuff.append(bil[(int)number]);
	    }
	    if (number >= 12 && number < 20) {
	      generate(number - 10);
	      stringBuff.append("Belas ");
	    }
	    if (number >= 20 && number < 100) {
	      generate(number / 10);
	      stringBuff.append("Puluh ");
	      generate(number % 10);
	    }
	    if (number >= 100 && number < 200) {
	      stringBuff.append("Seratus ");
	      generate(number % 100);
	    }
	    if (number >= 200 && number < 1000) {
	      generate(number / 100);
	      stringBuff.append("Ratus ");
	      generate(number % 100);
	    }
	    if (number >= 1000 && number < 2000) {
	      stringBuff.append("Seribu ");
	      generate(number % 1000);
	    }
	    if (number >= 2000 && number < 1000000) {
	      generate(number / 1000);
	      stringBuff.append("Ribu ");
	      generate(number % 1000);
	    }
	    if (number >= 1000000 && number < 1000000000) {
	      generate(number / 1000000);
	      stringBuff.append("Juta ");
	      generate(number % 1000000);
	    }
	    if (number >= 1000000000 ) {
	      generate(number / 1000000000);
	      stringBuff.append("Milyar ");
	      generate(number % 1000000000);
	    }
	    if(number==0&&stringBuff.length()<1){
	      stringBuff.append("Nol ");
	    }
	    return stringBuff.toString();
	  }
}
