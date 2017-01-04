package edu.wisc.cs.sdn.simpledns;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.omg.CORBA.portable.InputStream;

import edu.wisc.cs.sdn.simpledns.packet.DNS;
import edu.wisc.cs.sdn.simpledns.packet.DNSQuestion;
import edu.wisc.cs.sdn.simpledns.packet.DNSRdataAddress;
import edu.wisc.cs.sdn.simpledns.packet.DNSResourceRecord;

public class SimpleDNS {
	public static String rootIP;

	public static void main(String[] args) {
		List<String> argsList = Arrays.asList(args);
		int index;
		String ec2FileName;
		
        index = argsList.indexOf("-r") + 1;
        rootIP = argsList.get(index);

        index = argsList.indexOf("-e") + 1;
        ec2FileName = argsList.get(index);
        
        System.out.println("Read arguments");
        
        int portNumber = 8053;

		try{
			System.out.println("creating sockets");

			ServerSocket serverSocket = new ServerSocket(portNumber);
			Socket clientSocket = serverSocket.accept();
			System.out.println("waiting for input stream");
			
			InputStream stream = (InputStream) clientSocket.getInputStream();
			byte[] data = new byte[1000000];
			int count = stream.read(data);
			DNS dnsQuery = DNS.deserialize(data, count);

			System.out.println("***Received a DNS Query");

			DNS dnsReply = handleDNSQuery(dnsQuery);
			OutputStream outStream = (OutputStream) clientSocket.getOutputStream();
			byte[] outData = dnsReply.serialize();
			outStream.write(outData);
			
			serverSocket.close();

		} catch (IOException e) {
			System.out
					.println("Exception caught when trying to listen on port "
							+ portNumber + " or listening for a connection");
			System.out.println(e.getMessage());
		}
	}

	public static DNS handleDNSQuery(DNS dnsQuery) {
		if (dnsQuery.getOpcode() == 0) {
			boolean recursionDesired = dnsQuery.getRecursionDesired();
			
			System.out.println("***Handling DNS Query");
			if (!recursionDesired) {
				return queryDNSServer(rootIP, dnsQuery);
			}
			
			DNSQuestion question = dnsQuery.getQuestions().get(0);
			ArrayList<DNSResourceRecord> finalAnswers = new ArrayList<DNSResourceRecord>(); 
			
			String currDNSServerIP = rootIP;
			boolean queryDone = false;
			boolean reset = false;
			
			while(true){
				DNS dnsReply = queryDNSServer(currDNSServerIP, dnsQuery);
				for(DNSResourceRecord answer : dnsReply.getAnswers()){
					finalAnswers.add(answer);
					if(answer.getType() == question.getType()){
						queryDone = true;
					}
					else if(answer.getType() == DNS.TYPE_CNAME){
						question.setName(answer.getName());
						ArrayList<DNSQuestion> newQuestionList = new ArrayList<DNSQuestion>();
						newQuestionList.add(question);
						dnsQuery.setQuestions(newQuestionList);
						
						currDNSServerIP = rootIP;
						reset = true;
					}
				}
				
				if(reset){
					reset = false;
					continue;
				}
				
				if(queryDone){
					break;
				}
				
				DNSResourceRecord additional = (DNSResourceRecord) dnsReply.getAdditional().get(0);
				currDNSServerIP = ((DNSRdataAddress) additional.getData()).getAddress().toString();
			}
			
			dnsQuery.setAnswers(finalAnswers);
			return dnsQuery;
			
		} else {
			// drop the packet very very silently.
		}
		return null;
	}

	public static DNS queryDNSServer(String ip, DNS dnsQuery) {
		String hostName = ip;
		int dnsServerPort = 53;
		// add rootserver ips 198.41.0.4

		try (Socket socket = new Socket(hostName, dnsServerPort);) {
			OutputStream outStream = (OutputStream) socket.getOutputStream();
			byte[] outData = dnsQuery.serialize();
			outStream.write(outData);

			InputStream stream = (InputStream) socket.getInputStream();
			byte[] data = new byte[1000000];
			int count = stream.read(data);
			DNS dnsReply = DNS.deserialize(data, count);

			return dnsReply;
		} catch (IOException e) {
			System.err.println("Couldn't get I/O for the connection to "
					+ hostName);
			System.exit(1);
		}

		return null;
	}
}
