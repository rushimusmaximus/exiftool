package com.thebuzzmedia.exiftool;

/**
 * @author msgile
 * @author $LastChangedBy$
 * @version $Revision$  $LastChangedDate$
 * @since 1/16/14
 */
public abstract class TimeoutThread implements Runnable {

  private final String name;
  private final int timeoutMills;

  protected TimeoutThread(String name, int timeoutMills) {
    this.name = name;
    this.timeoutMills = timeoutMills;
  }

  public abstract void doWork();

  public abstract void onInterrupt();


  public void run() {
    Thread thread = new Thread(new Runnable() {
      public void run() {
        doWork();
      }
    },name);
    thread.start();
    try {
      thread.join(timeoutMills);
    } catch (InterruptedException ex) {
      onInterrupt();
      thread.interrupt();
      Thread.currentThread().interrupt();
      throw new RuntimeException(ex);
    }
  }
}
