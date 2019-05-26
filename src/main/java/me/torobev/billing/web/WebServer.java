package me.torobev.billing.web;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Slf4jRequestLog;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.core.json.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import me.torobev.billing.accounting.InMemoryAccounting;

import static java.lang.Integer.parseInt;
import static java.lang.Runtime.getRuntime;
import static java.lang.System.getProperty;

public class WebServer {

	private final int port;
	private Server server;


	public WebServer(int port) {
		this.port = port;
	}

	public void start() throws Exception {
		server = new Server(new QueuedThreadPool(2 * getRuntime().availableProcessors()));
		ServerConnector connector = new ServerConnector(server);
		connector.setPort(port);
		server.addConnector(connector);

		server.setRequestLog(new Slf4jRequestLog());
		JsonFactory factory = JsonFactory.builder().disable(StreamWriteFeature.AUTO_CLOSE_TARGET).build();
		ObjectMapper mapper = new ObjectMapper(factory);
		Handler handler = new Handler(mapper, new InMemoryAccounting());
		server.setHandler(handler);

		server.start();
	}

	public void stop() throws Exception {
		server.stop();
		server.join();
	}

	public void join() throws InterruptedException {
		server.join();
	}


	public static void main(String[] args) throws Exception {
		int port = parseInt(getProperty("server.port", "8080"));

		WebServer webServer = new WebServer(port);
		webServer.start();
		webServer.join();
	}

}
