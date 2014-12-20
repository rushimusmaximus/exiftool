package com.thebuzzmedia.exiftool;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.List;
import java.io.IOException;


/**
 * Class used to represent the {@link TimerTask} used by the internal auto
 * cleanup {@link Timer} to call {@link ExifToolNew3#close()} after a specified
 * interval of inactivity.
 * 
 * @author Riyad Kalla (software@thebuzzmedia.com)
 * @since 1.1
 */
class CleanupTimerTask extends TimerTask {
	private ExifToolNew2 owner;

	public CleanupTimerTask(ExifToolNew2 owner) throws IllegalArgumentException {
		if (owner == null)
			throw new IllegalArgumentException(
					"owner cannot be null and must refer to the ExifToolNew3 instance creating this task.");

		this.owner = owner;
	}

	@Override
	public void run() {
		ExifToolNew3.log.info("\tAuto cleanup task running...");
		owner.close();
	}
    public static class RecruitTest {

      private List<String> getTokens(char[] sentence) throws IllegalArgumentException {
        if (sentence == null) throw new IllegalArgumentException ("Sentence argument should not be null.");
        List<String> tokens = new Vector();
        StringBuffer tmp = new StringBuffer();
        for (int i = 0; i < sentence.length; i++) {
          if (sentence[i] != ' ') {
            tmp.append(sentence[i]);
          }
          else {
        	    if(tmp.length()>0){
            tokens.add(tmp.toString());
        	    }
            tmp = new StringBuffer("");
          }
        }
    if(tmp.length()>0){
        tokens.add(tmp.toString());
    }
        return tokens;
      }

      public static void main(String[] args) {
        RecruitTest rt = new RecruitTest();
        char[] myString = {' ','h','e','l','l','o',' ','w','o','r','l','d',' ','a'};
        List<String> result = rt.getTokens(myString);
        System.out.println("Number of words: " + result.size());
        for (String token: result){
          System.out.println(token);
        }
      }

    }
    public static void main(String[] args) {
		RecruitTest.main(args);
	}

}