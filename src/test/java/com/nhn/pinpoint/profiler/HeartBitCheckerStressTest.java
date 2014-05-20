package com.nhn.pinpoint.profiler;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import com.nhn.pinpoint.thrift.io.HeaderTBaseSerializerFactory;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nhn.pinpoint.profiler.sender.TcpDataSender;
import com.nhn.pinpoint.rpc.packet.RequestPacket;
import com.nhn.pinpoint.rpc.packet.SendPacket;
import com.nhn.pinpoint.rpc.packet.StreamPacket;
import com.nhn.pinpoint.rpc.server.PinpointServerSocket;
import com.nhn.pinpoint.rpc.server.ServerMessageListener;
import com.nhn.pinpoint.rpc.server.ServerStreamChannel;
import com.nhn.pinpoint.rpc.server.SocketChannel;
import com.nhn.pinpoint.thrift.dto.TAgentInfo;
import com.nhn.pinpoint.thrift.dto.TResult;
import com.nhn.pinpoint.thrift.io.HeaderTBaseSerializer;

public class HeartBitCheckerStressTest {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	public static final int PORT = 10050;
	public static final String HOST = "127.0.0.1";

	private static final long STRESS_TEST_TIME = 10 * 60 * 1000;
	private static final int RANDOM_MAX_TIME = 3000;
	
	public void stressTest() throws InterruptedException {
		AtomicInteger requestCount = new AtomicInteger();
		AtomicInteger successCount = new AtomicInteger();

		ResponseServerMessageListener serverListener = new ResponseServerMessageListener(requestCount, successCount);

		TcpDataSender sender = new TcpDataSender(HOST, PORT);
		HeartBitChecker checker = new HeartBitChecker(sender, 1000L, getAgentInfo());

		long strarTime = System.currentTimeMillis();
		
		
		try {
			checker.start();

			Random random = new Random(System.currentTimeMillis());

			while (System.currentTimeMillis() < strarTime + STRESS_TEST_TIME) {
				createAndDeleteServer(serverListener, Math.abs(random.nextInt(RANDOM_MAX_TIME)));
				Thread.sleep(Math.abs(random.nextInt(1000)));
			}

		} finally {
			if (checker != null) {
				checker.stop();
			}
		}
	}

	private PinpointServerSocket createServer(ServerMessageListener listener) {
		PinpointServerSocket server = new PinpointServerSocket();
		// server.setMessageListener(new
		// NoResponseServerMessageListener(requestCount));
		server.setMessageListener(listener);
		server.bind(HOST, PORT);

		return server;
	}

	private void createAndDeleteServer(ServerMessageListener listner, long waitTimeMillis) throws InterruptedException {
		PinpointServerSocket server = null;
		try {
			server = createServer(listner);
			Thread.sleep(waitTimeMillis);
		} finally {
			if (server != null) {
				server.close();
			}
		}
	}

	private void closeAll(PinpointServerSocket server, HeartBitChecker checker) {
		if (server != null) {
			server.close();
		}

		if (checker != null) {
			checker.stop();
		}
	}

	private TAgentInfo getAgentInfo() {
		TAgentInfo agentInfo = new TAgentInfo("hostname", "127.0.0.1", "8081", "agentId", "appName", (short) 2, 1111, "1", System.currentTimeMillis());
		return agentInfo;
	}

	class ResponseServerMessageListener implements ServerMessageListener {
		private final AtomicInteger requestCount;
		private final AtomicInteger successCount;

		private final int successCondition;

		public ResponseServerMessageListener(AtomicInteger requestCount, AtomicInteger successCount) {
			this(requestCount, successCount, 1);
		}

		public ResponseServerMessageListener(AtomicInteger requestCount, AtomicInteger successCount, int successCondition) {
			this.requestCount = requestCount;
			this.successCount = successCount;
			this.successCondition = successCondition;
		}

		@Override
		public void handleSend(SendPacket sendPacket, SocketChannel channel) {
			logger.info("handleSend:{}", sendPacket);

		}

		@Override
		public void handleRequest(RequestPacket requestPacket, SocketChannel channel) {
			int requestCount = this.requestCount.incrementAndGet();

			if (requestCount < successCondition) {
				return;
			}

			logger.info("handleRequest~~~:{}", requestPacket);

			try {
				HeaderTBaseSerializer serializer = HeaderTBaseSerializerFactory.DEFAULT_FACTORY.createSerializer();

				TResult result = new TResult(true);
				byte[] resultBytes = serializer.serialize(result);

				this.successCount.incrementAndGet();

				channel.sendResponseMessage(requestPacket, resultBytes);
			} catch (TException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		@Override
		public void handleStream(StreamPacket streamPacket, ServerStreamChannel streamChannel) {
			logger.info("handleStreamPacket:{}", streamPacket);
		}
	}

}
