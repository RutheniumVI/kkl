package ioio.examples.hello;

import ioio.lib.api.AnalogInput;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.IOIO;
import ioio.lib.api.IOIO.VersionType;
import ioio.lib.api.Sequencer;
import ioio.lib.api.Uart;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

public class MainActivity extends IOIOActivity {

	/**
	 * Initialization
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		int[] idList = {R.id.seekBar1, R.id.seekBar2,R.id.seekBar3,R.id.seekBar4,R.id.seekBar5,R.id.seekBar6};
		setContentView(R.layout.main);

	}

	/**
	 * This is the thread on which all the IOIO activity happens. It will be run
	 * every time the application is resumed and aborted when it is paused. The
	 * method setup() will be called right after a connection with the IOIO has
	 * been established (which might happen several times!). Then, loop() will
	 * be called repetitively until the IOIO gets disconnected.
	 */
	class Looper extends BaseIOIOLooper {
		/** The on-board LED. */
		private Sequencer.ChannelCueBinary stepperDirCue_ = new Sequencer.ChannelCueBinary();
		private Sequencer.ChannelCueSteps stepperStepCue_ = new Sequencer.ChannelCueSteps();
		private Sequencer.ChannelCue[] cue_ = new Sequencer.ChannelCue[] {stepperDirCue_, stepperStepCue_ };
		private Sequencer sequencer_;
		private AnalogInput sensorIn ;
		private float sensorI = 0f;

		private double time = 0;
		private double deltaT = 0;
		private double distance = 0;
		private boolean seen = false;
		private boolean seqStopped = false;
		private boolean goAgain = false;
		private boolean cont=true;

		private int rxPin = 3;
		private int txPin = 4;
		private int BAUDRATE = 115200;
		private Uart.Parity PARITY = Uart.Parity.NONE;
		private Uart.StopBits STOPBITS = Uart.StopBits.ONE;
		private OutputStream out;
		private InputStream in;
		PrintStream printout;
		private Uart uart;
		/**
		 * Called every time a connection with IOIO has been established.
		 * Typically used to open pins.
		 *
		 * @throws ConnectionLostException
		 *             When IOIO connection is lost.
		 *
		 * @see ioio.lib.util.IOIOLooper//#setup()
		 */
		@Override
		protected void setup() throws ConnectionLostException {
			showVersions(ioio_, "IOIO connected!");
			sensorIn = ioio_.openAnalogInput(40);
			uart = ioio_.openUart(rxPin, txPin, BAUDRATE, PARITY, STOPBITS);
			out = uart.getOutputStream();
			printout = new PrintStream(out);
			in = uart.getInputStream();
			sendCommand("#0 P700 #1 P1500 #2 P1500 #3 P600 #4 P2250\r");
			final Sequencer.ChannelConfigBinary stepperDirConfig = new Sequencer.ChannelConfigBinary(
					false, false, new DigitalOutput.Spec(5));
			final Sequencer.ChannelConfigSteps stepperStepConfig = new Sequencer.ChannelConfigSteps(
					new DigitalOutput.Spec(6));
			final Sequencer.ChannelConfig[] config = new Sequencer.ChannelConfig[] { stepperDirConfig,
					stepperStepConfig };

			sequencer_ = ioio_.openSequencer(config);
			enableUi(true);
			//ava = sequencer_.available();
			/*sequencer_.waitEventType(Sequencer.Event.Type.STOPPED);
			while (sequencer_.available() > 0) {
				push();
				push2();
			}*/
			sequencer_.start();
			sense();
		}
		@Override
		public void loop() throws ConnectionLostException, InterruptedException {
			if (cont == true) {
				if (seen == false) {
					if (seqStopped == true && goAgain == true) {
						sequencer_.start();
						seqStopped = false;
					}
					push();
				} else {
					sequencer_.stop();
					seqStopped = true;
					Thread.sleep(1000);
					sendCommand(getBaseValue(distance));
					sendCommand(getLengthValue(sensorI));
					Thread.sleep(2000);
					sendCommand("#4 P1400 T500\r");//close the gripper
					Thread.sleep(1000);
					sendCommand("#2 P1250 #3 P750  #1 P1280 T500\r");
					Thread.sleep(2000);
					sendCommand("#0 P1500 \r");//move the picked object
					Thread.sleep(2000);
					sendCommand("#4 P2250\r");//open gripper*/
						/*sequencer_ = ioio_.openSequencer(config);
						sequencer_.start();
						while(sequencer_.available() > 0) {
							push2();
						}
						double backTime = System.currentTimeMillis();
						while (System.currentTimeMillis() < (backTime + deltaT)) {
							push2();
						}
						sequencer_.stop();*/
					cont = false;

				}
			}
			sensorI = sensorIn.read();
		}
		private void sense(){
			Runnable r = new Runnable() {
				@Override
				public void run() {
					while (true) {
						try {
							if(Float.compare(sensorI,0.210000f)>=0){
								seen=true;
								//sequencer_.stop();
								deltaT = (System.currentTimeMillis()-time) /1000.0000;;
								float[] values = new float[20];
								for(int i=0;i<values.length;i++){
									values[i] = sensorIn.read();
								}
								float sensorValue = 0F;

								for (int i=0;i<values.length;i++){
									sensorValue+=values[i];
								}
								sensorValue = sensorValue/20.00F;
								distance+=(1.75 * deltaT);
								if(Float.compare(sensorValue,0.21f)>=0) {
									goAgain = false;

									break;
								}else{
									seen=false;
									goAgain = true;
									time = System.currentTimeMillis();
								}
							}
							//Thread.sleep(500);

						} catch (Exception e) {
						}
					}
				}
			};
			new Thread(r).start();
		}
		private void push() throws ConnectionLostException, InterruptedException {
			stepperStepCue_.clk = Sequencer.Clock.CLK_2M;
			stepperStepCue_.pulseWidth = 15;
			stepperStepCue_.period = 5000;
			stepperDirCue_.value = false;

			sequencer_.push(cue_, 62500);

		}
		private String getBaseValue(double distance){
			double x = (distance+3)/13.0*-600+1100;
			double y = 1300+x-500;
			return  "#0 P"+String.format("%.0f", x)+" #5 P"+String.format("%.0f", y)+" T1000\r";
		}
		private String getLengthValue(double distance){
			double x = (distance-0.11)/0.51*-600+1700;
			double y = 1400+x-1100;
			return  "#1 P"+String.format("%.0f", x)+" #2 P"+String.format("%.0f", y)+" T1000\r";
		}
		private void manualPush() throws ConnectionLostException, InterruptedException {
			stepperStepCue_.clk = Sequencer.Clock.CLK_2M;
			stepperStepCue_.pulseWidth = 15;
			stepperStepCue_.period = 5000;

			stepperDirCue_.value = false;

			sequencer_.manualStart(cue_);

		}
		private void push2() throws ConnectionLostException, InterruptedException {
			stepperStepCue_.clk = Sequencer.Clock.CLK_2M;
			stepperStepCue_.pulseWidth = 15;
			stepperStepCue_.period = 5000;
			stepperDirCue_.value = true;
			sequencer_.push(cue_, 62500);

		}
		private void push3() throws ConnectionLostException, InterruptedException {
			stepperStepCue_.clk = Sequencer.Clock.CLK_2M;
			stepperStepCue_.pulseWidth = 0;
			stepperStepCue_.period = 0;
			stepperDirCue_.value = true;
			sequencer_.push(cue_, 62500);


		}
		public void sendCommand(String a){
			printout.print(a);
			printout.flush();
		}

		/**
		 * Called when the IOIO is disconnected.
		 *
		 * @see ioio.lib.util.IOIOLooper#disconnected()
		 */
		@Override
		public void disconnected() {
			enableUi(false);
			toast("IOIO disconnected");
		}

		/**
		 * Called when the IOIO is connected, but has an incompatible firmware version.
		 *
		 * @see ioio.lib.util.IOIOLooper#incompatible(IOIO)
		 */
		@Override
		public void incompatible() {
			showVersions(ioio_, "Incompatible firmware version!");
		}
	}

	/**
	 * A method to create our IOIO thread.
	 *
	 * @see ioio.lib.util.AbstractIOIOActivity#createIOIOThread()
	 */
	@Override
	protected IOIOLooper createIOIOLooper() {
		return new Looper();
	}

	private void showVersions(IOIO ioio, String title) {
		toast(String.format("%s\n" +
						"IOIOLib: %s\n" +
						"Application firmware: %s\n" +
						"Bootloader firmware: %s\n" +
						"Hardware: %s",
				title,
				ioio.getImplVersion(VersionType.IOIOLIB_VER),
				ioio.getImplVersion(VersionType.APP_FIRMWARE_VER),
				ioio.getImplVersion(VersionType.BOOTLOADER_VER),
				ioio.getImplVersion(VersionType.HARDWARE_VER)));
	}

	private void toast(final String message) {
		final Context context = this;
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(context, message, Toast.LENGTH_LONG).show();
			}
		});
	}

	private int numConnected_ = 0;
	/*private void setOutput(final String a) {
		//final String str = String.format("%.4f", a);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				outputText.setText("");
				outputText.append(a);
				outputText.append("\n");
			}
		});

	}
	private void setNumber(float a) {
		final String str = String.format("%.4f", a);

			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					textView.setText(str);
					outputText.append(str);
					outputText.append("\n");
				}
			});

	}*/
	private void enableUi(final boolean enable) {
		// This is slightly trickier than expected to support a multi-IOIO use-case.
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (enable) {
					if (numConnected_++ == 0) {
					}
				} else {
					if (--numConnected_ == 0) {
					}
				}
			}
		});
	}
}