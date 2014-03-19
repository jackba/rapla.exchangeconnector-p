package org.rapla.plugin.exchangeconnector.client;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import org.rapla.components.layout.TableLayout;
import org.rapla.entities.configuration.Preferences;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.DefaultPluginOption;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.gui.toolkit.RaplaButton;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorPlugin;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorRemote;
import org.rapla.plugin.exchangeconnector.SynchronizationStatus;

public class ExchangeConnectorUserOptions extends DefaultPluginOption  {

   // private static final String DEFAULT_DISPLAYED_VALUE = "******";
    //private Preferences preferences;

    //private String exchangeUsername;
//    private String exchangePassword;
    //private boolean downloadFromExchange;
    //private boolean enableSynchronisation;
    //FilterEditButton filter;
    private JPanel optionsPanel;
    private JCheckBox enableNotifyBox;
    //private JCheckBox downloadFromExchangeBox;
    //private JLabel securityInformationLabel;
    private JLabel usernameLabel;
    private JLabel usernameInfoLabel;
    private JLabel unsynchronizedLabel;
    //private JTextField filterCategoryField;
    //private String filterCategory;
    //private JLabel eventTypesLabel;
//    private JList eventTypesList;
    ExchangeConnectorRemote service;
    RaplaButton loginButton;
    RaplaButton syncButton;
    RaplaButton removeButton;
    RaplaButton retryButton;
    
	private boolean connected;
    
    public ExchangeConnectorUserOptions(RaplaContext raplaContext,ExchangeConnectorRemote service) throws Exception {
        super(raplaContext);
        setChildBundleName(ExchangeConnectorConfig.RESOURCE_FILE);
        this.service = service;
    }

    public JComponent getComponent() {
        return optionsPanel;
    }

    public String getName(Locale locale) {
        return "Exchange Connector";
    }

    public void show() throws RaplaException {
        initJComponents();
        setValuesToJComponents();
    }

    public void setPreferences(Preferences preferences) {
        this.preferences = preferences;
    }

    public void commit() throws RaplaException {
//        if (applyUsersettings()) {
//            saveUsersettings();
//        }

    	//String exchangeUsername = usernameTextField.getText();
    	//String exchangePassword = new String(passwordTextField.getPassword());
    	preferences.putEntry(ExchangeConnectorConfig.EXCHANGE_SEND_INVITATION_AND_CANCELATION,enableNotifyBox.isSelected());
        //preferences.putEntry(ExchangeConnectorConfig.SYNC_FROM_EXCHANGE_ENABLED_KEY, downloadFromExchangeBox.isSelected());
//        preferences.putEntry(ExchangeConnectorConfig.EXCHANGE_INCOMING_FILTER_CATEGORY_KEY, filterCategoryField.getText());
//        preferences.putEntry(ExchangeConnectorConfig.EXPORT_EVENT_TYPE_KEY, getSelectedEventTypeKeysAsCSV());

        //getWebservice(ExchangeConnectorRemote.class).setDownloadFromExchange(downloadFromExchangeBox.isSelected());
   
    }

 
    private void initJComponents() {
        this.optionsPanel = new JPanel();
        usernameLabel = new JLabel();
        usernameInfoLabel = new JLabel();
        unsynchronizedLabel = new JLabel();

        double[][] sizes = new double[][]{
                {5, TableLayout.PREFERRED, 5, TableLayout.FILL, 5},
                {TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED, 5,
                        TableLayout.PREFERRED}};

        TableLayout tableLayout = new TableLayout(sizes);
        this.optionsPanel.setLayout(tableLayout);
        this.optionsPanel.add(new JLabel("Exchange Login"), "1, 2");
        loginButton = new RaplaButton();
        syncButton = new RaplaButton();
        removeButton = new RaplaButton();
        retryButton = new RaplaButton();
        loginButton.setText("Set Login");
        removeButton.setText("Remove connection");
        syncButton.setText("ReSync");
        retryButton.setText("retry");
        usernameInfoLabel.setText( "exchange user");
        usernameLabel.setText("not connected");
        this.optionsPanel.add(usernameInfoLabel, "1, 0");
        this.optionsPanel.add(usernameLabel, "3, 0");
        enableNotifyBox = new JCheckBox("Send mail on invitation/cancelation");
        this.optionsPanel.add(loginButton, "3, 2");
        this.optionsPanel.add(removeButton, "3, 4");
        this.optionsPanel.add(syncButton, "3, 6");
        this.optionsPanel.add(this.enableNotifyBox, "1,8");
        this.optionsPanel.add(unsynchronizedLabel, "1, 10");
        this.optionsPanel.add(retryButton, "3, 10");
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
            	
            	boolean modal = true;
				String[] options = new String[] {getString("connect"),getString("abort")};
				final SyncDialog content = new SyncDialog();
				if ( connected)
				{
					String text = usernameLabel.getText();
					content.init(text);
				}
				try
				{
					final DialogUI dialog = DialogUI.create(getContext(), getComponent(), modal, content, options);
					dialog.setTitle( "Exchange Login");
					dialog.getButton( 0).setAction( new AbstractAction() {
						
						private static final long serialVersionUID = 1L;

						@Override
						public void actionPerformed(ActionEvent e) {
							String username = content.getUsername();
							String password = content.getPassword();
						     try {
						    	 service.changeUser(username,password);
						     } catch (RaplaException ex) {
			                	 showException(ex, getMainComponent());
			                	 return;
			                 }
						     dialog.close();
						}
					});
					dialog.start();
					updateComponentState();
				}
				catch (RaplaException ex)
				{
					 showException(ex, getMainComponent());
                     getLogger().error("The operation was not successful!", ex);
				}
            }
        });
        syncButton.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				try
				{
					service.synchronize();
					updateComponentState();
				}
				catch (RaplaException ex)
				{
					 showException(ex, getMainComponent());
                     getLogger().error("The operation was not successful!", ex);
				}
				
			}
		});
        removeButton.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				try
				{
					service.removeUser();
					updateComponentState();
				}
				catch (RaplaException ex)
				{
					 showException(ex, getMainComponent());
                     getLogger().error("The operation was not successful!", ex);
				}
			}
		});
        retryButton.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				try
				{
					service.retry();
					updateComponentState();
				}
				catch (RaplaException ex)
				{
					 showException(ex, getMainComponent());
                     getLogger().error("The operation was not successful!", ex);
				}
			}
		});
        
        
//        this.filterCategoryField = new JTextField();
//        this.eventTypesLabel = new JLabel(getString("event.raplatypes"));
//        this.eventTypesList = new JList();
//        this.eventTypesList.setVisibleRowCount(5);
//        this.eventTypesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
//
//        this.enableSynchronisationBox = new JCheckBox(getString("enable.sync.rapla.exchange"));
//        UpdateComponentsListener updateComponentsListener = new UpdateComponentsListener();
//        this.enableSynchronisationBox.addActionListener(updateComponentsListener);
//        this.downloadFromExchangeBox = new JCheckBox(getString("enable.sync.exchange.rapla"));
//        this.downloadFromExchangeBox.addActionListener(updateComponentsListener);
//        this.securityInformationLabel = new JLabel(getString("security.info"));
//        this.filterCategoryLabel = new JLabel(getString("category.filter"));
//          this.optionsPanel.add(this.usernameLabel, "1,2");
//      this.optionsPanel.add(this.usernameTextField, "3,2");
//      this.optionsPanel.add(this.passwordLabel, "1,4");
//      this.optionsPanel.add(this.passwordTextField, "3,4");
//      this.optionsPanel.add(this.eventTypesLabel, "1,6");
//      this.optionsPanel.add(filter.getButton(), "3,6");
//
//          this.optionsPanel.add(this.downloadFromExchangeBox, "1,8");
//        this.optionsPanel.add(this.filterCategoryLabel, "1,10");
//        this.optionsPanel.add(this.filterCategoryField, "3,10");
//        this.optionsPanel.add(this.securityInformationLabel, "3,12");

    }

    private void setValuesToJComponents() throws RaplaException {
    	boolean enableNotify = preferences.getEntryAsBoolean(ExchangeConnectorConfig.EXCHANGE_SEND_INVITATION_AND_CANCELATION, ExchangeConnectorConfig.DEFAULT_EXCHANGE_SEND_INVITATION_AND_CANCELATION);
    	enableNotifyBox.setSelected( enableNotify);
    	
//        downloadFromExchange = preferences.getEntryAsBoolean(ExchangeConnectorConfig.SYNC_FROM_EXCHANGE_ENABLED_KEY, ExchangeConnectorConfig.DEFAULT_SYNC_FROM_EXCHANGE_ENABLED);
//        filterCategory = preferences.getEntryAsString(ExchangeConnectorConfig.EXCHANGE_INCOMING_FILTER_CATEGORY_KEY, ExchangeConnectorConfig.DEFAULT_EXCHANGE_INCOMING_FILTER_CATEGORY);
//	String eventTypeKeys = preferences.getEntryAsString(ExchangeConnectorConfig.EXPORT_EVENT_TYPE_KEY, ExchangeConnectorConfig.DEFAULT_EXPORT_EVENT_TYPE);
//	usernameTextField.setText( preferences.getEntryAsString( ExchangeConnectorConfig.USERNAME, ""));
//    passwordTextField.setText( preferences.getEntryAsString( ExchangeConnectorConfig.PASSWORD, ""));
//    downloadFromExchangeBox.setSelected(ExchangeConnectorConfig.ENABLED_BY_ADMIN && downloadFromExchange);
//    filterCategoryField.setText(filterCategory);
//
//    final DefaultListModel model = new DefaultListModel();
//    try {
//        DynamicType[] dynamicTypes = getClientFacade().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
//		for (DynamicType event : dynamicTypes) {
//            // event type of "import from exchange" will (for now not) be ignored!
//            String elementKey = event.getElementKey();
//			String iMPORT_EVENT_TYPE = ExchangeConnectorConfig.IMPORT_EVENT_TYPE;
//			if (!iMPORT_EVENT_TYPE.equalsIgnoreCase(elementKey))
//                model.addElement(new StringWrapper<DynamicType>(event));
//        }
//    } catch (RaplaException e) {
//    }
//    eventTypesList.setModel(new SortedListModel(model));
//    eventTypesList.setModel(model);
//    selectEventTypesInListFromCSV(eventTypeKeys);
    	updateComponentState();
    }

    public Class<? extends PluginDescriptor<?>> getPluginClass() {
        return ExchangeConnectorPlugin.class;
    }
    
    class SyncDialog extends JPanel
    {
        private static final long serialVersionUID = 1L;
		private JTextField usernameTextField;
        private JPasswordField passwordTextField;
        private JLabel usernameLabel;
        private JLabel passwordLabel;
        {
            this.usernameTextField = new JTextField();
            usernameTextField.setEnabled( !connected);
            this.passwordTextField = new JPasswordField();
            this.passwordLabel = new JLabel(getString("password.server"));
            this.usernameLabel = new JLabel(getString("username.server"));
            double[][] sizes = new double[][]{
                    {5, TableLayout.PREFERRED, 5, 200, 5},
                    {TableLayout.PREFERRED, 5,TableLayout.PREFERRED}
            };
            setLayout( new TableLayout( sizes));
            add( usernameLabel, "1,0");
            add( usernameTextField, "3,0");
            add( passwordLabel, "1,2");
            add( passwordTextField, "3,2");
        }
        
        public void init(String username)
        {
        	usernameTextField.setText( username);
        }
        
        public String getUsername()
        {
        	return usernameTextField.getText();
        }
        public String getPassword()
        {
        	return new String(passwordTextField.getPassword());
        }
        
        
    }

    private void updateComponentState() throws RaplaException {
    	SynchronizationStatus synchronizationStatus = service.getSynchronizationStatus();
    	this.connected = synchronizationStatus.enabled;
    	this.usernameLabel.setText(  connected ? synchronizationStatus.username: "not connected");
    	int unsynchronizedEvents = synchronizationStatus.unsynchronizedEvents;
    	unsynchronizedLabel.setText(unsynchronizedEvents + " unsynchronized Events");
    	Color foreground = usernameLabel.getForeground();
    	if ( foreground != null)
    	{
    		unsynchronizedLabel.setForeground( unsynchronizedEvents > 0 ? Color.RED : foreground);
    	}
    	this.loginButton.setText( connected ? "change password" : "connect");
    	this.enableNotifyBox.setEnabled( connected);
    	this.removeButton.setEnabled( connected);
    	this.syncButton.setEnabled( connected);
    	this.retryButton.setEnabled( connected && unsynchronizedEvents > 0);

    }
//	enableSynchronisationBox.setEnabled(ExchangeConnectorConfig.ENABLED_BY_ADMIN);
//    syncButton.setEnabled( enableSynchronisationBox.isSelected());
//    passwordTextField.setEnabled(enableSynchronisationBox.isSelected());
//    downloadFromExchangeBox.setEnabled(ExchangeConnectorConfig.ENABLED_BY_ADMIN && enableSynchronisationBox.isSelected());
//    filterCategoryField.setEnabled(ExchangeConnectorConfig.ENABLED_BY_ADMIN && enableSynchronisationBox.isSelected() && downloadFromExchangeBox.isSelected());
//    filter.getButton().setEnabled( enableSynchronisationBox.isSelected());
//}
//private void setValuesToJComponents() {
//	boolean    enableSynchronisation = preferences.getEntryAsBoolean(ExchangeConnectorConfig.ENABLED_BY_USER_KEY, ExchangeConnectorConfig.DEFAULT_ENABLED_BY_USER);
//        downloadFromExchange = preferences.getEntryAsBoolean(ExchangeConnectorConfig.SYNC_FROM_EXCHANGE_ENABLED_KEY, ExchangeConnectorConfig.DEFAULT_SYNC_FROM_EXCHANGE_ENABLED);
//        filterCategory = preferences.getEntryAsString(ExchangeConnectorConfig.EXCHANGE_INCOMING_FILTER_CATEGORY_KEY, ExchangeConnectorConfig.DEFAULT_EXCHANGE_INCOMING_FILTER_CATEGORY);
//	String eventTypeKeys = preferences.getEntryAsString(ExchangeConnectorConfig.EXPORT_EVENT_TYPE_KEY, ExchangeConnectorConfig.DEFAULT_EXPORT_EVENT_TYPE);
//	usernameTextField.setText( preferences.getEntryAsString( ExchangeConnectorConfig.USERNAME, ""));
//    passwordTextField.setText( preferences.getEntryAsString( ExchangeConnectorConfig.PASSWORD, ""));
//    enableSynchronisationBox.setSelected( enableSynchronisation);
//    downloadFromExchangeBox.setSelected(ExchangeConnectorConfig.ENABLED_BY_ADMIN && downloadFromExchange);
//    filterCategoryField.setText(filterCategory);
//
//    final DefaultListModel model = new DefaultListModel();
//    try {
//        DynamicType[] dynamicTypes = getClientFacade().getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
//		for (DynamicType event : dynamicTypes) {
//            // event type of "import from exchange" will (for now not) be ignored!
//            String elementKey = event.getElementKey();
//			String iMPORT_EVENT_TYPE = ExchangeConnectorConfig.IMPORT_EVENT_TYPE;
//			if (!iMPORT_EVENT_TYPE.equalsIgnoreCase(elementKey))
//                model.addElement(new StringWrapper<DynamicType>(event));
//        }
//    } catch (RaplaException e) {
//    }
//    eventTypesList.setModel(new SortedListModel(model));
//    eventTypesList.setModel(model);
//    selectEventTypesInListFromCSV(eventTypeKeys);
//    updateComponentState();
//}
//    private class UpdateComponentsListener implements ActionListener {
//        public void actionPerformed(ActionEvent e) {
//            updateComponentState();
//        }
//    }

}






