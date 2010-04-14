package org.intrace.agent.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.intrace.agent.ClassTransformer;
import org.intrace.output.AgentHelper;
import org.intrace.shared.AgentConfigConstants;
import org.intrace.shared.OutputConfigConstants;
import org.intrace.shared.TraceConfigConstants;

/**
 * Server thread handling a single connected client.
 */
public class AgentClientConnection implements Runnable
{
  private final Socket connectedClient;
  private final ClassTransformer transformer;
  private final AgentServer serverRef;

  /**
   * cTor
   * 
   * @param agentServer
   * @param xiConnectedClient
   * @param xiTransformer
   */
  public AgentClientConnection(AgentServer agentServer,
      Socket xiConnectedClient, ClassTransformer xiTransformer)
  {
    super();
    serverRef = agentServer;
    connectedClient = xiConnectedClient;
    transformer = xiTransformer;
    System.out.println("## Connected to: " + xiConnectedClient.getPort());
  }

  /**
   * Main client loop
   * <ul>
   * <li>Receive a message and send a response.
   * </ul>
   * Special Messages:
   * <ul>
   * <li>getsettings - Return a complete configuration Map
   * <li>help - Return a Set of all the supported commands
   * </ul>
   */
  @Override
  public void run()
  {
    try
    {
      boolean quit = false;
      while (!quit)
      {
        try
        {
          String message = receiveMessage();
          if (message.equals("getsettings"))
          {
            Map<String, String> settingsMap = new HashMap<String, String>();
            settingsMap.putAll(transformer.getSettings());
            settingsMap.putAll(AgentHelper.getSettings());
            serverRef.broadcastMessage(this, settingsMap);
          }
          else if (message.equals("help"))
          {
            Set<String> commandSet = new HashSet<String>();
            commandSet.addAll(AgentConfigConstants.COMMANDS);
            commandSet.addAll(OutputConfigConstants.COMMANDS);
            commandSet.addAll(TraceConfigConstants.COMMANDS);
            sendMessage(commandSet);
          }
          else
          {
            List<String> responses = transformer.getResponse(message);
            if (responses.size() > 0)
            {
              for (String response : responses)
              {
                sendMessage(response);
              }
            }
            else
            {
              sendMessage("OK");
            }
          }
        }
        catch (IOException e)
        {
          quit = true;
        }
      }
      System.out.println("## Disconnected from: " + connectedClient.getPort());
      connectedClient.close();
    }
    catch (IOException e1)
    {
      e1.printStackTrace();
    }
    finally
    {
      serverRef.removeClientConnection(this);
    }
  }

  /**
   * Synchronously receive a String message.
   * 
   * @return
   * @throws IOException
   */
  private String receiveMessage() throws IOException
  {
    InputStream in = connectedClient.getInputStream();
    ObjectInputStream objIn = new ObjectInputStream(in);
    try
    {
      return (String) objIn.readObject();
    }
    catch (ClassNotFoundException e)
    {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Synchronously send an Object message.
   * 
   * @param xiObject
   * @throws IOException
   */
  public void sendMessage(Object xiObject) throws IOException
  {
    OutputStream out = connectedClient.getOutputStream();
    ObjectOutputStream objOut = new ObjectOutputStream(out);
    objOut.writeObject(xiObject);
    objOut.flush();
  }

  /**
   * Start the Client connection - create a new, named, daemon thread.
   */
  public void start(int clientNum)
  {
    Thread clientThread = new Thread(this);
    clientThread.setDaemon(true);
    clientThread.setName("AgentServer-Client" + clientNum);
    clientThread.start();
  }
}