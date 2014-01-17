package com.thebuzzmedia.exiftool;

/**
 * @author msgile
 * @author $LastChangedBy$
 * @version $Revision$  $LastChangedDate$
 * @since 1/16/14
 */
public abstract class TimeoutThread {

  private final String name;
  private final int timeoutMills;
  private volatile boolean done = false;

  protected TimeoutThread(String name, int timeoutMills) {
    this.name = name;
    this.timeoutMills = timeoutMills;
  }

  private void runWork() {
    done = false;
    try {
      doWork();
    } finally {
      done = true;
    }
  }

  public abstract void doWork();

  public abstract void onInterrupt();


  public void start() {
    Thread thread = new Thread(new Runnable() {
      public void run() {
        runWork();
      }
    },name);
    thread.start();
    try {
      thread.join(timeoutMills);
    } catch (InterruptedException ex) {
      thread.interrupt();
      onInterrupt();
      throw new RuntimeException(ex);
    }
    if ( ! done ) {
      thread.interrupt();
      onInterrupt();
      throw new RuntimeException(name+" ran too long, "+timeoutMills+" mills");
    }
  }
}
