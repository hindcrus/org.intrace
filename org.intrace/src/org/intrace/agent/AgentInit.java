package org.intrace.agent;

import java.lang.instrument.Instrumentation;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.intrace.agent.server.AgentClientConnection;
import org.intrace.agent.server.AgentServer;
import org.intrace.output.AgentHelper;
import org.intrace.output.trace.TraceHandler;

public class AgentInit
{

  static int serverPort = -1;

  /**
   * Common init function.
   *
   * @param agentArgs
   * @param inst
   */
  public static void initialize(String agentArgs, Instrumentation inst)
  {
    System.out.println("## Loaded InTrace Agent.");

    if (agentArgs == null)
    {
      agentArgs = "";
    }

    // Setup the trace instrumentation handler
    AgentHelper.setInstrumentationHandler(TraceHandler.INSTANCE);

    // Parse startup args
    AgentSettings args = new AgentSettings(agentArgs);
    AgentHelper.getResponses(null, agentArgs);

    // Construct Transformer
    ClassTransformer t = new ClassTransformer(inst, args);
    inst.addTransformer(t, inst.isRetransformClassesSupported());

    // Ensure loaded classes are traced
    t.instrumentKlasses(t.getLoadedClassesForModification());

    // Start Server thread
    new AgentServer(t, args.getServerPort()).start();

    // Store server port
    waitForServerPort();
    args.setActualServerPort(serverPort);

    // Wait for callback connection
    if (args.getCallbackPort() > -1)
    {
      System.out.println("## Establishing Callback Connection...");
      doCallbackConnection(args.getCallbackPort(), t);
    }

    // Wait for startup
    if (args.isWaitStart())
    {
      try
      {
        System.out.println("## Program Paused");
        AgentServer.waitForStartSignal();
      }
      catch (InterruptedException e)
      {
        e.printStackTrace();
      }
    }

    // Setup shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread()
    {
      @Override
      public void run()
      {
        AgentHelper.gracefulShutdown();
      }
    });
  }

  public static synchronized void setServerPort(int xiServerPort)
  {
    serverPort = xiServerPort;
    AgentInit.class.notifyAll();
  }

  static synchronized void waitForServerPort()
  {
    try
    {
      while (serverPort == -1)
      {
        AgentInit.class.wait();
      }
    }
    catch (InterruptedException e)
    {
      e.printStackTrace();
    }
  }

  /**
   * This is a mechanism which allows a client to listen on a particular port and have the
   * Agent server make the initial connection. This is useful when the client launches the Agent
   * and wants to connect as soon as the Agent starts up. This is used by the Eclipse plugin.
   * @param callbackPort
   * @param t
   */
  static void doCallbackConnection(int callbackPort, ClassTransformer t)
  {
    try
    {
      Socket callback = new Socket();
      callback.connect(new InetSocketAddress("localhost", callbackPort));
      AgentClientConnection clientConnection = new AgentClientConnection(
                                                                         callback,
                                                                         t);
      AgentServer.addClientConnection(clientConnection);
      clientConnection.start(1);
      clientConnection.waitForTraceConn();
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
    }
  }

}
