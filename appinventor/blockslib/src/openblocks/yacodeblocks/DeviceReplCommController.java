// Copyright 2009 Google Inc. All Rights Reserved.

package openblocks.yacodeblocks;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Class for communication with a Phone/Emulator containing an embedded Yail REPL.
 * Handles the lower levels of communication with a device.
 *
 * @author markf@google.com (Mark Friedman)
 */

public class DeviceReplCommController implements AndroidController.DeviceConnectionListener {

  public interface PostProcessor {
    void postProcess(String message);
    void onFailure(Throwable e);
    void onDisconnect(String serialNumber);
    void onConnect(String serialNumber);
  }

  private static final boolean DEBUG = true;

  private String host;
  private int port;
  private Socket socket;
  private OutputStream out;
  private InputStream in;
  private ReadWriteThread inputThread;
  private AndroidController androidController;
  private PostProcessor postProcessor;

  private volatile boolean connectionHappy = false;  // is there a device connected and working
  private volatile int devicesPluggedIn = 0;

  public DeviceReplCommController(String host, int port,
      AndroidController androidController, PostProcessor postProcessor) {
    this.host = host;
    this.port = port;
    this.androidController = androidController;
    this.postProcessor = postProcessor;
  }

  private void doWrite(String message) throws IOException {
    out.write(message.getBytes("UTF-8"));
    out.write('\n');
    if (DEBUG) {
      System.out.println(String.format("Sent '%s\n", message));
    }
  }
  
/**
 * Send initial string to the REPL controller.  If the REPL controller
 * isn't actually connected, will take a series of escalating steps to
 * reconnect it.
 * @param message the string to send to the REPL controller
 * @param mustRestartApp if true, will restart the app before doing the send
 * @throws IOException, ExternalStorageException
 */
  public void sendInitial(String message, boolean mustRestartApp) throws IOException,
      ExternalStorageException {
    String selectedDevice = androidController.getSelectedDevice();
    if (DEBUG) {
      System.out.println("ReplController sending " + message);
      System.out.println("**** devicesPluggedIn: " + devicesPluggedIn
                          + " selectedDevice: " + selectedDevice
                          + " connectionHappy: " + connectionHappy);
    }
    // TODO(kerr): deal with threading with respect to devices plugged in?

    // if think is connected, try a write.
    // if that fails, start debugging.

    String failedMessage = "Failed to restart communication for unknown reason.";
    if (connectionHappy && !mustRestartApp) {
      // we _think_ that it is connected...
      try {
        doWrite(message);
        connectionHappy = true;
        return;
      } catch (IOException e) {
        if (DEBUG) {
          System.out.println("**** Exception while writing: " + e.getMessage());
          System.out.println("     ... ignoring and trying to reconnect.");
        }
      }
    }
    connectionHappy = false;
    if (selectedDevice != null) {
      // Only check if there is a selected device

      /*
       *  Always reinstall app when there is a problem. The new "tight security"
       *  app will only ever accept one socket connection and disables itself
       *  as soon as it starts, so it cannot be restarted.
       */
      boolean restarted = false; // If true, failure was probably at the socket level
      try {
        if (DEBUG) {
          System.out.println("**** Trying to do restart from reinstalling the application....");
        }
        doReinstallApplication();
        doRestartApplication();
        restarted = true;
        establishSocketLevelCommunication();
        doWrite(message);
        connectionHappy = true;
        return;
      } catch (IOException e) {
        if (DEBUG) {
          System.out.println("**** Reinstall was not enough: " + e.getMessage());
        }
        if (restarted) {
          androidController.androidRestartBridge();
          // try exponential back-off for opening the socket
          int sleepTime = 3000;  // start with 3 seconds
          for (int i = 1; i <=4 ; i++) {  // 4 tries at most = 45 sec. total
            try {
              if (DEBUG) {
                System.out.println("     ... waiting " + sleepTime/1000 +
                " seconds and then trying again to establish connection");
              }
              Thread.sleep(sleepTime);
              establishSocketLevelCommunication();
              doWrite(message);
              connectionHappy = true;
              return;
            } catch (Exception e2) {
              if (i == 4) {
                if (DEBUG) {
                  System.out.println("**** giving up on connecting to phone: "
                      + e2.getMessage());
                }
              } else {
                if (DEBUG) {
                  System.out.println("Failed to connect: " + e2.getMessage());
                }
                sleepTime = sleepTime * 2; // double and try again
              }
            }
          }
        }
        failedMessage = e.getMessage();
      }
    } else {
      if (DEBUG) {
        System.out.println("***** No device selected");
      }
      throw new IOException("It appears that no device has been selected");
    }
    connectionHappy = false;

    // there was nothing plugged in, or somehow we got unplugged by now
    if (devicesPluggedIn == 0) {
      String errorMessage = "App Inventor cannot find any connected devices.\n";
      throw new IOException(errorMessage);
    }

    if (DEBUG) {
      System.out.println("**** devicesPluggedIn: " + devicesPluggedIn
                         + " selectedDevice: " + selectedDevice
                         + " connectionHappy: " + connectionHappy);
    }

    throw new IOException(failedMessage);
  }

  /**
   * Send a string to the REPL controller.  Expects that the controller is
   * already happily running.
   * @param message the string to send to the REPL controller
   * @throws IOException
   */
  public void send(String message) throws IOException {
    if (DEBUG) {
      System.out.println("ReplController sending " + message);
    }
    // TODO(kerr): how deal with threading with respect to devices plugged in?

    if (connectionHappy) {
      // we _think_ that it is connected...
      doWrite(message);
    } else {
      throw new IOException("Connection is not happy and needs restart.");
    }
  }

  private void establishSocketLevelCommunication() throws IOException {
    // Unfortunately, need to reforward the port for every new socket connection.
    reset();
    if (! forwardAndroidPort()) {
      throw new IOException("Could not forward TCP port.");
    }
    if (DEBUG) {
      System.out.println("trying to establish communication");
    }
    try {
      setupSocket();
    } catch (IOException e) {
      if (DEBUG) {
        System.out.println("Failed in setupSocket: " + e.getMessage());
      }
      throw e;
    }
  }

  private void setupSocket() throws IOException {
    if (inputThread != null) {
      inputThread.stopRunning();
    }
    socket = new Socket(host, port);
    // Note that this call does not fail even if there's nothing running on
    // the other end.  Also, there's no problem getting the streams.  Sigh.
    out = socket.getOutputStream();
    in = socket.getInputStream();
    int ch = in.read(); // We'll put it back before the thread tries to read it.
    if (ch < 0) {
      throw new IOException("Input stream closed before found anything on it.");
    }
    inputThread = new ReadWriteThread(ch);
    inputThread.start();
  }

  private boolean forwardAndroidPort() {
    boolean result = androidController.androidForwardTcpPort(
        PhoneCommManager.REPL_COMMUNICATION_PORT,
        PhoneCommManager.REPL_COMMUNICATION_PORT);
    if (DEBUG) {
      if (result) {
        System.out.println("ADB forward command was successfully run");
      } else {
        System.out.println("ADB forward command failed to run");
      }
    }
    return result;
  }

  /**
   * This function can take as long as 10 seconds since it sleeps (in .1
   * second increments) while waiting for the application to restart.
   * Usually does not take more than two or three seconds, sometimes much
   * less.
   */
  private void doRestartApplication() throws IOException {
    try {
      androidController.androidKillStarterApplication();
      androidController.androidStartStarterApplication();
      int attempts = 0;
      while (attempts < 10) {
        // Unfortunately, the test if the application is running is not as
        // good as a test if it is actually responding.
        Thread.sleep(1000);
        if (androidController.androidIsStarterApplicationRunning()) {
          return;
        }
        attempts++;
      }
      // Sometimes we can't tell if it's running but it seems to be doing
      // so.  We'll catch the error later if it really didn't start.
      if (DEBUG) {
        System.out.println("Allegedly failed to start application - waited too long.");
      }
    } catch (AndroidControllerException e) {
      throw new IOException("Failed to start application: " + e.getMessage());
    } catch (java.lang.InterruptedException e) {
      throw new IOException("Failed to start application: " + e.getMessage());
    }
  }

 private void doReinstallApplication() throws IOException, ExternalStorageException {
    if (DEBUG) {
      System.out.println("Trying to sync and install on the device...");
    }
    try {
      androidController.androidSyncAndInstallStarterApplication();
    } catch (AndroidControllerException e) {
      throw new IOException(e.getMessage());
    }
  }

  public void reset() {
    try {
      if (inputThread != null) {
        inputThread.stopRunning();
      }
      if (socket != null) {
        socket.close();
        socket = null;
      }
    } catch (IOException e) {
      if (DEBUG) {
        System.out.println("Problem closing REPL socket");
      }
    }
  }

  public void deviceConnected(String serialNumber) {
    devicesPluggedIn++;
    if (DEBUG) {
      System.out.println("Device connected: " + devicesPluggedIn +
          " devices plugged in.");
    }
    postProcessor.onConnect(serialNumber);
  }

  public void deviceDisconnected(String serialNumber) {
    devicesPluggedIn--;

    if (DEBUG) {
      System.out.println("Device disconnected: " + devicesPluggedIn +
          " devices plugged in.");
    }
    if (devicesPluggedIn < 0) {
      if (DEBUG) {
        System.out.println("XXXXXX We're confused about number of connected devices. Yikes!");
      }
      devicesPluggedIn = 0;
    } else if (serialNumber.equals(androidController.getSelectedDevice())) {
      // If the user unplugged the device we were connected to, we care.  Otherwise not.
      connectionHappy = false;
    }
    postProcessor.onDisconnect(serialNumber);
  }
  
  public String getSelectedDevice() {
    return androidController.getSelectedDevice();
  }
  
  public boolean selectDevice(String device) {
    try {
      androidController.selectDevice(device);
      return true;
    } catch (AndroidControllerException e) {
      return false;
    }
  }

  class ReadWriteThread extends Thread {

    private volatile boolean shouldRun = true;
    private volatile int initialCh = -1;

    ReadWriteThread(int ch) {
      super();
      initialCh = ch;
    }

    public void stopRunning() {
      this.shouldRun = false;
    }

    @Override
    public void run ()
    {
      try {
        byte[] buffer = new byte[1024];
        while (shouldRun) {
          int ch;
          if (initialCh != -1) {
            ch = initialCh;
            initialCh = -1;
          } else {
            ch = in.read();
          }
          if (ch < 0)
            break; // ??? FIXME
          buffer[0] = (byte) ch;
          int avail = in.available();
          if (avail > 0) {
            if (avail > buffer.length - 1)
              avail = buffer.length - 1;
            avail = in.read(buffer, 1, avail);
          }
          String received = new String(buffer, 0, avail + 1, "UTF-8");
          if (postProcessor != null) {
            postProcessor.postProcess(received);
          }
        }
      } catch (java.io.IOException ex) {
        if (shouldRun) {
          // exception wasn't due to us being reset
          if (DEBUG) {
            System.out.println("ReadWriteThread got exception " + ex);
            ex.printStackTrace();
          }
          postProcessor.onFailure(ex);
        }
      }
    }
  }
}
