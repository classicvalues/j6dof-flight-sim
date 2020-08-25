/*******************************************************************************
 * Copyright (C) 2016-2020 Christopher Ali
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 *  If you have any questions about this project, you can visit
 *  the project's GitHub repository at: http://github.com/chris-ali/j6dof-flight-sim/
 ******************************************************************************/
package com.chrisali.javaflightsim.initializer;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import com.chrisali.javaflightsim.interfaces.SimulationController;
import com.chrisali.javaflightsim.lwjgl.LWJGLWorld;
import com.chrisali.javaflightsim.lwjgl.events.WindowClosedListener;
import com.chrisali.javaflightsim.simulation.SimulationRunner;
import com.chrisali.javaflightsim.simulation.flightcontrols.SimulationEventListener;
import com.chrisali.javaflightsim.simulation.integration.Integrate6DOFEquations;
import com.chrisali.javaflightsim.simulation.integration.SimOuts;
import com.chrisali.javaflightsim.simulation.setup.Options;
import com.chrisali.javaflightsim.simulation.setup.SimulationConfiguration;
import com.chrisali.javaflightsim.simulation.setup.Trimming;
import com.chrisali.javaflightsim.simulation.utilities.FileUtilities;
import com.chrisali.javaflightsim.swing.GuiFrame;
import com.chrisali.javaflightsim.swing.consoletable.ConsoleTablePanel;
import com.chrisali.javaflightsim.swing.plotting.PlotWindow;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Controls the configuration and running of processes supporting the simulation component of JavaFlightSim. This consists of: 
 * <p>The simulation engine that integrates the 6DOF equations ({@link Integrate6DOFEquations})</p>
 * <p>Initialization and control of the LWJGL out the window (OTW) world ({@link LWJGLWorld})</p>
 * <p>Initializing the Swing GUI menus</p>
 * <p>Plotting of the simulation states and data ({@link PlotWindow})</p>
 * <p>Raw data display of simulation states ({@link ConsoleTablePanel})</p>
 * 
 * @author Christopher Ali
 *
 */
public class LWJGLSwingSimulationController implements SimulationController, WindowClosedListener, SimulationEventListener {
	
	//Logging
	private static final Logger logger = LogManager.getLogger(LWJGLSwingSimulationController.class);
	
	// Configuration
	private SimulationConfiguration configuration;
	private EnumSet<Options> options;
			
	// Simulation and Threads
	private SimulationRunner runner;
	private Thread runnerThread;
	
	// Menus and Integrated Simulation Window
	private GuiFrame guiFrame;
	
	// Plotting
	private PlotWindow plotWindow;
	
	// Raw Data Console
	private ConsoleTablePanel consoleTablePanel;

	private boolean wasReset = false;
		
	/**
	 * Initializes initial settings, configurations and conditions to be edited through menu options
	 */
	public LWJGLSwingSimulationController(SimulationConfiguration configuration) {
		this.configuration = configuration;
		guiFrame = new GuiFrame(this);
	}
	
	//============================== Configuration =========================================================
	
	/**
	 * @return instance of configuraion
	 */
	@Override
	public SimulationConfiguration getConfiguration() { return configuration; }
	
	//=============================== Simulation ===========================================================

	/**
	 * Initializes, trims and starts the flight controls, simulation (and flight and environment data, if selected) threads.
	 * Depending on options specified, a console panel and/or plot window will also be initialized and opened 
	 */
	@Override
	public void startSimulation() {
		if (runner != null && runner.isRunning()) {
			logger.warn("Simulation is already running! Please wait until it has finished");
			return;
		}
		
		configuration = FileUtilities.readSimulationConfiguration();
		options = configuration.getSimulationOptions();
			
		logger.info("Starting simulation...");
		
		logger.info("Trimming aircraft...");
		Trimming.trimSim(configuration, false);
		
		logger.info("Initializing simulation runner...");
		runner = new SimulationRunner(this);

		logger.info("Initializaing and starting simulation runner thread...");
		runnerThread = new Thread(runner);
		runnerThread.start();
	}
	
	/**
	 * Stops simulation and data transfer threads (if running), closes the raw data {@link ConsoleTablePanel},
	 * {@link SimulationWindow}, and opens the main menus window again
	 */
	@Override
	public void stopSimulation() {
		logger.info("Stopping simulation...");

		runner.setRunning(false);	
		
		logger.info("Returning to menus...");
		guiFrame.setVisible(true);
	}
			
	/**
	 * When LWJGL OTW window is closed, this event is fired
	 */
	@Override
	public void onWindowClosed() {
		stopSimulation();	
	}
	
	/**
	 * Stops the simulation
	 */
	@Override
	public void onStopSimulation() {
		stopSimulation();
	}
	
	/**
	 * Pauses and unpauses the simulation 
	 */
	@Override
	public void onPauseUnpauseSimulation() {
		if(!options.contains(Options.PAUSED)) {
			options.add(Options.PAUSED);
		} else {
			options.remove(Options.PAUSED);
			options.remove(Options.RESET);
			wasReset = false;
		} 
	}

	/**
	 * When the simulation is paused, it can be reset back to initial conditions once per pause 
	 */
	@Override
	public void onResetSimulation() {
		if(options.contains(Options.PAUSED) && !options.contains(Options.RESET) && !wasReset) {
			options.add(Options.RESET);
			logger.debug("Resetting simulation to initial conditions...");
			wasReset = true;
		}
	}

	/**
	 * Generates plots of the simulation thus far
	 */
	@Override
	public void onPlotSimulation() {
		plotSimulation();
	}

	/**
	 * @return if simulation is running
	 */
	@Override
	public boolean isSimulationRunning() {
		return (runner != null && runner.isRunning());
	}
	
	/**
	 * @return ArrayList of simulation output data 
	 * @see SimOuts
	 */
	public List<Map<SimOuts, Double>> getLogsOut() {
		return (runner != null) ? runner.getSimulation().getLogsOut() : null;
	}
	
	/**
	 * @return if simulation was able to clear data kept in logsOut
	 */
	public boolean clearLogsOut() {
		return (runner != null && runner.isRunning()) ? runner.getSimulation().clearLogsOut() : false;
	}
		
	//=============================== Plotting =============================================================
	
	/**
	 * Initializes the plot window if not already initialized, otherwise refreshes the window and sets it visible again
	 */
	@Override
	public void plotSimulation() {
		logger.info("Plotting simulation results...");
		
		try {
			if(plotWindow != null)
				plotWindow.setVisible(false);
				
			plotWindow = new PlotWindow(this);		
		} catch (Exception e) {
			logger.error("An error occurred while generating plots!", e);
		}
	}
	
	/**
	 * @return Instance of the plot window
	 */
	public PlotWindow getPlotWindow() { return plotWindow; }

	//=============================== Console =============================================================
	
	/**
	 * Initializes the raw data console window and starts the auto-refresh of its contents
	 */
	@Override
	public void initializeConsole() {
		try {
			logger.info("Starting flight data console...");
			
			if(consoleTablePanel != null)
				consoleTablePanel.setVisible(false);
			
			consoleTablePanel = new ConsoleTablePanel(this);
			consoleTablePanel.startTableRefresh();			
		} catch (Exception e) {
			logger.error("An error occurred while starting the console panel!", e);
		}
	}
}
