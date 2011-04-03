package org.puredata.jack;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;

import org.puredata.core.PdBase;

import com.noisepages.nettoyeur.jack.JackException;
import com.noisepages.nettoyeur.jack.JackNativeClient;

public class PdJackProcessor {

	private static final int PD_BLOCK_SIZE = PdBase.blockSize();
	
	private final int nIn;
	private final int nOut;
	private final float[] inBuf;
	private final float[] outBuf;
	
	public PdJackProcessor(int nIn, int nOut) throws JackException {
		if ((nIn < 0 || nOut < 0) || (nIn == 0 && nOut == 0)) {
			throw new IllegalArgumentException("bad number of input/output channels");
		}
		this.nIn = nIn;
		this.nOut = nOut;
		this.inBuf = new float[nIn * PD_BLOCK_SIZE];
		this.outBuf = new float[nOut * PD_BLOCK_SIZE];
		PdBase.openAudio(nIn, nOut, JackNativeClient.getSampleRate(), 1);
		PdBase.computeAudio(true);
	}
	
	public JackNativeClient createClient(String name) throws JackException {
		return new JackNativeClient(name, nIn, nOut) {
			@Override
			protected void process(FloatBuffer[] inputs, FloatBuffer[] outputs) {
				int jackBlockSize = (nIn > 0) ? inputs[0].capacity() : outputs[0].capacity();
				assert jackBlockSize % PD_BLOCK_SIZE == 0;
				for (int n = 0; n < jackBlockSize; n += PD_BLOCK_SIZE) {
					for (int i = 0; i < nIn; i++) {
						inputs[i].get(inBuf, i*PD_BLOCK_SIZE, PD_BLOCK_SIZE);
					}
					PdBase.processRaw(inBuf, outBuf);
					for (int i = 0; i < nOut; i++) {
						outputs[i].put(outBuf, i*PD_BLOCK_SIZE, PD_BLOCK_SIZE);
					}
				}
			}
		};
	}
	
	public static void main(String[] args) throws JackException, InterruptedException, IOException {
		PdJackProcessor proc = new PdJackProcessor(1, 2);
		int patchId = PdBase.openPatch(new File("data/test.pd"));
		JackNativeClient jackClient = proc.createClient("pd-client");
		jackClient.connectInputPorts("system");
		jackClient.connectOutputPorts("system");
		while (true) {
			Thread.sleep(100);
		}
	}
}