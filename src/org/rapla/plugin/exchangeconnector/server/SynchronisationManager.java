package org.rapla.plugin.exchangeconnector.server;

import static org.rapla.entities.configuration.CalendarModelConfiguration.EXPORT_ENTRY;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorPlugin;
import org.rapla.plugin.exchangeconnector.SynchronizationStatus;
import org.rapla.plugin.exchangeconnector.SynchronizeResult;
import org.rapla.plugin.exchangeconnector.server.SynchronizationTask.SyncStatus;
import org.rapla.plugin.exchangeconnector.server.exchange.AppointmentSynchronizer;
import org.rapla.plugin.exchangeconnector.server.exchange.EWSConnector;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.RaplaKeyStorage.LoginInfo;
import org.rapla.server.TimeZoneConverter;
import org.rapla.storage.UpdateOperation;
import org.rapla.storage.UpdateResult;


public class SynchronisationManager extends RaplaComponent implements ModificationListener {
	ExchangeAppointmentStorage appointmentStorage;
	ExchangeConnectorConfig.ConfigReader config;
	RaplaKeyStorage keyStorage;
	Map<String,List<CalendarModelImpl>> calendarModels = new HashMap<String,List<CalendarModelImpl>>();
	protected ReadWriteLock lock = new ReentrantReadWriteLock();

	public SynchronisationManager(RaplaContext context) throws RaplaException {
		super(context);
		
		final ClientFacade clientFacade =  context.lookup(ClientFacade.class);
		clientFacade.addModificationListener(this);
        keyStorage = context.lookup( RaplaKeyStorage.class);
        this.config  = new ExchangeConnectorConfig.ConfigReader(clientFacade.getSystemPreferences().getEntry( ExchangeConnectorConfig.EXCHANGESERVER_CONFIG, new RaplaConfiguration()));
        this.appointmentStorage = context.lookup( ExchangeAppointmentStorage.class);
        for ( User user : getClientFacade().getUsers())
        {
        	updateCalendarMap( user, false);
        }
        CommandScheduler scheduler = context.lookup( CommandScheduler.class);
        long delay =0;
		long period = DateTools.MILLISECONDS_PER_HOUR * 2;
		scheduler.schedule( new RetryCommand(), delay, period);
        //final Timer scheduledDownloadTimer = new Timer("ScheduledDownloadThread",true);
        //scheduledDownloadTimer.schedule(new ScheduledDownloadHandler(context, clientFacade, getLogger()), 30000, ExchangeConnectorPlugin.PULL_FREQUENCY*1000);
	}
	
	class RetryCommand implements Command
	{
		public void execute() throws Exception {
			Collection<SynchronizationTask> allTasks = appointmentStorage.getAllTasks();
			SynchronisationManager.this.execute(allTasks);
		}
	}
	public synchronized void dataChanged(ModificationEvent evt) throws RaplaException {
		synchronize((UpdateResult) evt);
	}
	
	public synchronized SynchronizeResult retry(User user) throws RaplaException  
	{
		Collection<SynchronizationTask> existingTasks = appointmentStorage.getTasks(user);
		return execute( existingTasks);
	}
	
	public SynchronizationStatus getSynchronizationStatus(User user) throws RaplaException 
	{
	    SynchronizationStatus result = new SynchronizationStatus();
        LoginInfo secrets = keyStorage.getSecrets(user, ExchangeConnectorServerPlugin.EXCHANGE_USER_STORAGE);
        boolean connected = secrets != null;
        result.enabled = connected;
        result.username = secrets != null ? secrets.login :"";
        if ( secrets != null)
        {
            Collection<SynchronizationTask> existingTasks = appointmentStorage.getTasks(user);
            for ( SynchronizationTask task:existingTasks)
            {
                SyncStatus status = task.getStatus();
                if (status.isOpen( ))
                {
                    result.unsynchronizedEvents++;
                }
                if (status == SyncStatus.synched)
                {
                    result.synchronizedEvents++;
                }
            }
        }
        return result;
	}
	
	
	public synchronized SynchronizeResult synchronizeUser(User user) throws RaplaException  {
	    Collection<SynchronizationTask> tasks = new HashSet<SynchronizationTask>();
		{
    		Collection<SynchronizationTask> existingTasks = appointmentStorage.getTasks(user);
			for ( SynchronizationTask task:existingTasks)
    		{
    			task.setStatus( SyncStatus.toDelete);
    			tasks.add( task);
    		}
		}
		// then insert and update the new tasks
		Collection<SynchronizationTask> updateTasks = updateCalendarMap(user, true);
		tasks.addAll( updateTasks);
		// we skip notification on a resync
		SynchronizeResult result = execute( tasks, true);
		return result;
    }

	protected Collection<SynchronizationTask> updateTasks(Appointment appointment, boolean remove) throws RaplaException  {
		Collection<SynchronizationTask> result = new HashSet<SynchronizationTask>();
		if ( remove){
     		Collection<SynchronizationTask> taskList = appointmentStorage.getTasks( appointment);
        	for (SynchronizationTask task:taskList)
        	{
        		task.setStatus( SyncStatus.toDelete);
        		result.add( task );
        	}
 		}
 		else
 		{
 			 if ( isInSyncInterval( appointment))
 	         {
 				 result.addAll(appointmentStorage.getTasks(appointment));
 				 Collection<String> matchingUserIds = findMatchingUser( appointment);
 				 for( String userId:matchingUserIds)
 				 {
 					 SynchronizationTask task = addOrUpdateAppointment(appointment,userId);
 					 result.add( task );
 				 }
 	         }
 		}
		return result;
	}
		
    private Collection<String> findMatchingUser(Appointment appointment) throws RaplaException {
    	Set<String> result = new HashSet<String>();
		Lock lock = readLock();
		try	{
			for (String userId :calendarModels.keySet())
			{
				List<CalendarModelImpl> list = calendarModels.get(userId);
				for ( CalendarModelImpl conf:list)
				{
					if (conf.isMatchingSelectionAndFilter( appointment))
					{
						result.add( userId);
						break;
					}
				}
			}
			
		} finally {
			unlock( lock);
		}
		return result;
	}

	public synchronized void synchronize(UpdateResult evt) throws RaplaException {
        Collection<SynchronizationTask> tasks = new ArrayList<SynchronizationTask>();
        
        for (UpdateOperation operation: evt.getOperations())
		{
			Entity current = operation.getCurrent();
			if ( current.getRaplaType() ==  Reservation.TYPE )
			{
				if ( operation instanceof UpdateResult.Remove)
				{
					Reservation oldReservation = (Reservation) current;
					for ( Appointment app: oldReservation.getAppointments() )
					{
						Collection<SynchronizationTask> result = updateTasks( app, true);
						tasks.addAll(result);
					}
				}
				if ( operation instanceof UpdateResult.Add)
				{
					Reservation newReservation = (Reservation) ((UpdateResult.Add) operation).getNew();
					for ( Appointment app: newReservation.getAppointments() )
					{
						Collection<SynchronizationTask> result =  updateTasks(  app, false);
						tasks.addAll(result);
					}
				}
				if ( operation instanceof UpdateResult.Change)
				{
					Reservation oldReservation = (Reservation) ((UpdateResult.Change) operation).getOld();
					Reservation newReservation =(Reservation) ((UpdateResult.Change) operation).getNew();
					Set<Appointment> oldAppointments =  new HashSet<Appointment>(Arrays.asList(oldReservation.getAppointments()));
					Set<Appointment> newAppointments =  new HashSet<Appointment>(Arrays.asList(newReservation.getAppointments()));
					for ( Appointment oldApp: oldAppointments)
					{
						if ( newAppointments.contains( oldApp))
						{
							continue;
						}
						Collection<SynchronizationTask> result =  updateTasks(  oldApp, true);
						tasks.addAll(result);
					}
					for ( Appointment newApp: newAppointments)
					{
						boolean notChanged =false;
						if ( oldAppointments.contains( newApp))
						{
							for ( Appointment oldApp: oldAppointments)
							{
								if ( oldApp.equals( newApp))
								{
									if ( oldApp.matches( newApp))
									{
										Allocatable[] oldAllocatables = oldReservation.getAllocatablesFor( oldApp);
										Allocatable[] newAllocatables = newReservation.getAllocatablesFor( newApp);
										if (Arrays.equals( oldAllocatables,  newAllocatables))
										{
											notChanged = true;
										}
									}
								}
							}
						}
						if ( notChanged )
						{
							continue;
						}
						Collection<SynchronizationTask> result =  updateTasks(  newApp, false);
						tasks.addAll(result);
					}
				}
			}
			if ( current.getRaplaType() ==  Preferences.TYPE )
			{
				Preferences preferences = (Preferences)operation.getCurrent();
				if ( !(operation instanceof UpdateResult.Remove))
				{
					User owner = preferences.getOwner();
					if ( owner != null)
					{
						Collection<SynchronizationTask> result =  updateCalendarMap(owner, false);
						tasks.addAll(result);
					}
				}
			}
			if ( current.getRaplaType() ==  User.TYPE )
			{
				String userId = current.getId();
				Lock lock = writeLock();
				try	{
					calendarModels.remove( userId);
				} finally {
					unlock( lock);
				}
			}
		}

        evt.getAddObjects();
        if ( tasks.size() > 0)
		{
			appointmentStorage.addOrReplace( tasks);
			execute( tasks );
		}
    }

	protected Lock writeLock() throws RaplaException {
		return RaplaComponent.lock( lock.writeLock(), 60);
	}

	protected Lock readLock() throws RaplaException {
		return RaplaComponent.lock( lock.readLock(), 10);
	}

    private Collection<SynchronizationTask> updateCalendarMap(User user, boolean addUpdate) throws RaplaException 
    {
    	Collection<SynchronizationTask> result = new HashSet<SynchronizationTask>();
    	boolean createIfNotNull = false;
    	String userId = user.getId();
		Preferences preferences = getQuery().getPreferences(user, createIfNotNull);
		if ( preferences == null)
		{
			Lock lock = writeLock();
			try	{
				this.calendarModels.remove( userId);
			} finally {
				unlock( lock);
			}
			return result;
		}
		CalendarModelConfiguration modelConfig = preferences.getEntry(CalendarModelConfiguration.CONFIG_ENTRY);
        Map<String,CalendarModelConfiguration> exportMap= preferences.getEntry(CalendarModelConfiguration.EXPORT_ENTRY);
        if ( modelConfig == null && exportMap == null)
        {
        	Lock lock = writeLock();
			try	{
				this.calendarModels.remove( userId);
			} finally {
				unlock( lock);
			}
        	return result;
        }
        List<CalendarModelImpl> configList = new ArrayList<CalendarModelImpl>();
        if ( modelConfig!= null)
        {
        	if ( hasExchangeExport( modelConfig))
        	{
        		Collection<SynchronizationTask> updateTasks = updateTasks(user, modelConfig, configList, addUpdate);
				result.addAll(updateTasks);
        	}
        }
        
        if ( exportMap != null)
        {
        	for ( String key:exportMap.keySet())
        	{
        		CalendarModelConfiguration calendarModelConfiguration = exportMap.get( key);
        		if ( hasExchangeExport( calendarModelConfiguration))
            	{
        			Collection<SynchronizationTask> updateTasks = updateTasks(user, calendarModelConfiguration, configList, addUpdate);
            		result.addAll(updateTasks);
            	}
        	}
        }
        Lock lock = writeLock();
		try	{
    		if ( configList.size() > 0)
            {
    			this.calendarModels.put( userId, configList);
            }
    		else
    		{
    			this.calendarModels.remove( userId);
    		}
		} finally {
			unlock( lock);
		}
		return result;
	}

	protected Collection<SynchronizationTask> updateTasks(User user,CalendarModelConfiguration modelConfig,List<CalendarModelImpl> configList, boolean addUpdated) throws RaplaException {
		String userId = user.getId();
		
		Set<SynchronizationTask> result = new HashSet<SynchronizationTask>();
		CalendarModelImpl calendarModelImpl = new CalendarModelImpl(getContext(), user, getClientFacade());
		Map<String, String> alternativOptions = null;
		calendarModelImpl.setConfiguration( modelConfig, alternativOptions);
		configList.add( calendarModelImpl);
		TimeInterval syncRange = getSyncRange();
		Collection<Appointment> appointments = calendarModelImpl.getAppointments(syncRange);
		for ( Appointment app:appointments)
		{
			SynchronizationTask task = appointmentStorage.getTask(app, userId);
			// add new appointments to the appointment store, we don't need to check for updates here as this, will be triggered by a reservation change
			if ( task == null)
			{
				task = appointmentStorage.createTask(app, userId);
				result.add( task);
			} 
			else if ( addUpdated)
			{
				task.setStatus( SyncStatus.toReplace);
				result.add(  task );
			}
			
		}
		return result;
	}

	private boolean hasExchangeExport(CalendarModelConfiguration modelConfig) {
		String option = modelConfig.getOptionMap().get(ExchangeConnectorPlugin.EXCHANGE_EXPORT);
		if ( option != null && option.equals("true"))
		{
			return true;
		}
		return false;
	}

	public synchronized SynchronizationTask addOrUpdateAppointment(Appointment appointment,String userId) throws RaplaException {
    	SynchronizationTask task = appointmentStorage.getTask( appointment,userId);
    	if ( task == null)
    	{
    		task = appointmentStorage.createTask(appointment, userId);
    	}
    	task.setStatus( SyncStatus.toUpdate);
    	return task;
    }
    
	public SynchronizeResult execute(Collection<SynchronizationTask> tasks) throws RaplaException {
		return execute( tasks, false);
	}
	
	private SynchronizeResult execute(Collection<SynchronizationTask> tasks, boolean skipNotification) throws RaplaException {
		Collection<SynchronizationTask> toStore = new HashSet<SynchronizationTask>();
		Collection<SynchronizationTask> toRemove = new HashSet<SynchronizationTask>();
		SynchronizeResult result = processTasks(tasks, toStore, toRemove, skipNotification);
		appointmentStorage.storeAndRemove(toStore, toRemove);
		return result;
	}

	protected SynchronizeResult processTasks(Collection<SynchronizationTask> tasks,Collection<SynchronizationTask> toStore,Collection<SynchronizationTask> toRemove, boolean skipNotification) {
		SynchronizeResult result = new SynchronizeResult();
	    for ( SynchronizationTask task:tasks)
		{
			 final AppointmentSynchronizer worker; 
			 String userId = task.getUserId();
			 EntityResolver resolver = getClientFacade().getOperator();
			 String appointmentId = task.getAppointmentId();
			 Appointment appointment;
			 User user;
			 try
			 {
			 	// we don't resolve the appointment if we delete 
				 appointment = task.getStatus() != SyncStatus.toDelete  ? resolver.tryResolve( appointmentId, Appointment.class) : null;
				 user = resolver.resolve( userId, User.class);
			 } catch (EntityNotFoundException e) {
				 getLogger().info( "Removing synchronize " + task + " due to " + e.getMessage() );
				 toRemove.add( task);
				 continue;
			 }
			 SyncStatus before = task.getStatus();
			 if ( appointment != null && !isInSyncInterval( appointment) || before == SyncStatus.deleted)
			 {
				 toRemove.add( task);
				 continue;
			 }
			 if ( before == SyncStatus.synched)
			 {
				 continue;
			 }
			 try
			 {
				 LoginInfo secrets = keyStorage.getSecrets( user, ExchangeConnectorServerPlugin.EXCHANGE_USER_STORAGE);
				 if ( secrets != null)
				 {
					 String username = secrets.login;
					 String password = secrets.secret;
					 boolean notificationMail;
					 if ( skipNotification)
					 {
						 notificationMail = false;
					 }
					 else
					 {
						 Preferences preferences = getQuery().getPreferences( user);
						 notificationMail = preferences.getEntryAsBoolean( ExchangeConnectorConfig.EXCHANGE_SEND_INVITATION_AND_CANCELATION, ExchangeConnectorConfig.DEFAULT_EXCHANGE_SEND_INVITATION_AND_CANCELATION);
					 }
					 RaplaContext context = getContext();
					 Logger logger = getLogger();
					 TimeZoneConverter converter = context.lookup( TimeZoneConverter.class);
					 worker = new AppointmentSynchronizer(logger, config,converter,task, appointment,user,username,password, notificationMail);
				 }
				 else
				 {
					 getLogger().info( "User no longer connected to Exchange " );
					 toRemove.add( task);
					 continue;
				 }
			 } 
			 catch (RaplaException ex)
			 {
				 getLogger().error( "Internal error while processing SynchronizationTask " + task  +". Ignoring task. ", ex);
				 continue;
			 }
			 try
			 {
				 worker.execute();
			 } catch (Exception e) {
				 getLogger().warn( "Can't synchronize " + task +  " Cause "  + e.getMessage() );
				 task.increaseRetries();
				 toStore.add( task);
				 result.open++;
				 continue;
			 }
			 SyncStatus after = task.getStatus();
			 switch (after)
			 {
		    	case deleted: toRemove.add( task);break;
		    	case synched: break;
		    	case toDelete: 
		    	case toReplace:
		    	case toUpdate:  task.increaseRetries(); toStore.add( task);

			 }
			 if ( after != before)
			 {
				 if ( after == SyncStatus.synched)
				 {
					 toStore.add( task);
					 result.changed ++;
				 }
				 if ( after == SyncStatus.deleted)
                 {
				     result.removed ++;
                 }
			 }
		}
	    return result;
	}
        
    private TimeInterval getSyncRange()
    {
    	final ClientFacade clientFacade = getClientFacade();
    	Date today = clientFacade.today();
    	Date start = DateTools.addDays(today, -config.get(ExchangeConnectorConfig.SYNCING_PERIOD_PAST));
    	Date end = DateTools.addDays(today, config.get(ExchangeConnectorConfig.SYNCING_PERIOD_FUTURE));
    	return new TimeInterval(start, end);
    }

    private boolean isInSyncInterval( Appointment appointment)  {
    	Date start = appointment.getStart();
		TimeInterval appointmentRange = new TimeInterval(start, appointment.getMaxEnd());
		TimeInterval syncRange = getSyncRange();
		if ( !syncRange.overlaps( appointmentRange))
		{
		    getLogger().debug("Skipping update of appointment " + appointment + " because is date of item is out of range");
	        return false;
		}
		else 
		{
			return true;
		}
    }

	public void removeTasksAndExports(User user) throws RaplaException 
	{
		String userId = user.getId();
		appointmentStorage.removeTasks( userId);
		Lock lock = writeLock();
		try	{
			this.calendarModels.remove( userId);
		} finally {
			unlock( lock);
		}
		boolean createIfNotNull = false;
		Preferences preferences = getQuery().getPreferences(user, createIfNotNull);
		if ( preferences == null)
		{
			return;
		}
		preferences = getModification().edit( preferences);
		CalendarModelConfiguration modelConfig = preferences.getEntry(CalendarModelConfiguration.CONFIG_ENTRY);
        if ( modelConfig != null )
        {
        	Map<String, String> optionMap = modelConfig.getOptionMap();
        	if ( optionMap.containsKey(ExchangeConnectorPlugin.EXCHANGE_EXPORT))
        	{
        	    Map<String,String> newMap = new LinkedHashMap<String, String>( optionMap);
        	    newMap.remove( ExchangeConnectorPlugin.EXCHANGE_EXPORT);
        	    CalendarModelConfiguration newConfig = modelConfig.cloneWithNewOptions(newMap);
        	    preferences.putEntry( CalendarModelConfiguration.CONFIG_ENTRY, newConfig);
        	}
        }
        Map<String,CalendarModelConfiguration> exportMap= preferences.getEntry(CalendarModelConfiguration.EXPORT_ENTRY);
        if ( exportMap != null)
        {
            Map<String,CalendarModelConfiguration> newExportMap = new TreeMap<String,CalendarModelConfiguration>( exportMap);
            for ( String key:exportMap.keySet())
        	{
        		CalendarModelConfiguration calendarModelConfiguration = exportMap.get( key);
                Map<String, String> optionMap = calendarModelConfiguration.getOptionMap();
        		if ( optionMap.containsKey(ExchangeConnectorPlugin.EXCHANGE_EXPORT))
                {
                    Map<String,String> newMap = new LinkedHashMap<String, String>( optionMap);
                    newMap.remove( ExchangeConnectorPlugin.EXCHANGE_EXPORT);
                    CalendarModelConfiguration newConfig = calendarModelConfiguration.cloneWithNewOptions(newMap);
                    newExportMap.put( key, newConfig);
                }
        	}
            preferences.putEntry( EXPORT_ENTRY, getModification().newRaplaMap( newExportMap ));
        }
		getModification().store( preferences);
	}

	public void testConnection(String exchangeUsername, String exchangePassword) throws RaplaException {
		String fqdn = config.get(ExchangeConnectorConfig.EXCHANGE_WS_FQDN);
		EWSConnector connector = new EWSConnector(fqdn, exchangeUsername, exchangePassword);
		try {
			connector.test();
		} catch (Exception e) {
			throw new RaplaException("Kann die Verbindung zu Exchange nicht herstellen: " + e.getMessage());
		}
	}

	

    

}
