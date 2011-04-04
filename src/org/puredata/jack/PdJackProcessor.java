/**
 * 
 * For information on usage and redistribution, and for a DISCLAIMER OF ALL
 * WARRANTIES, see the file, "LICENSE.txt," in this distribution.
 * 
 */

package org.puredata.jack;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;

import org.puredata.core.PdBase;

import com.noisepages.nettoyeur.jack.JackException;
import com.noisepages.nettoyeur.jack.JackNativeClient;

/**
 * A simple class that ties Pure Data and Jack together. (Not thread-safe!)
 * 
 * @author Peter Brinkmann (peter.brinkmann@gmail.com)
 */
public class PdJackProcessor {

	private static final int PD_BLOCK_SIZE = PdBase.blockSize();
	private static PdJackProcessor instance = null;
	
	private final int nIn;
	private final int nOut;
	private final float[] inBuf;
	private final float[] outBuf;
	private JackNativeClient nativeClient = null;
	
	/**
	 * Initializes Pd and prepares input and output buffers for Jack.
	 * 
	 * @param nIn number of input channels
	 * @param nOut number of output channels
	 */
	private PdJackProcessor(int nIn, int nOut) throws JackException {
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
	
	/**
	 * This method makes sure that there's only one instance of this class at any given time;
	 * an unfortunate limitation, but it's necessary as long as Pd only admits one global instance.
	 * 
	 * @param nIn number of input channels
	 * @param nOut number of output channels
	 */
	public static PdJackProcessor createPdJackProcessor(int nIn, int nOut) throws JackException {
		if (instance != null) {
			instance.stop();
		}
		instance = new PdJackProcessor(nIn, nOut);
		return instance;
	}
	
	/**
	 * Creates a new Jack client for this processor.  Since it only makes sense to have one
	 * Jack client for Pd at any given time, this method will close the current client if
	 * necessary.
	 * 
	 * @param name
	 */
	public JackNativeClient createClient(String name) throws JackException {
		stop();
		nativeClient = new JackNativeClient(name, nIn, nOut) {
			@Override
			protected void process(FloatBuffer[] inputs, FloatBuffer[] outputs) {
				int jackBlockSize = (nIn > 0) ? inputs[0].capacity() : outputs[0].capacity();
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
		return nativeClient;
	}
	
	private void stop(){ 
		if (nativeClient != null) {
			nativeClient.close();
			nativeClient = null;
		}
	}
	
	// Simple main routine, mostly to check whether everything loads and starts correctly.
	public static void main(String[] args) throws JackException, InterruptedException, IOException {
		PdJackProcessor proc = createPdJackProcessor(1, 2);
		int patchId = PdBase.openPatch(new File("data/test.pd"));
		JackNativeClient jackClient = proc.createClient("pd-client");
		jackClient.connectInputPorts("system");
		jackClient.connectOutputPorts("system");
		Thread.sleep(1000 * 3600);
		PdBase.closePatch(patchId);
	}
}