cekDocumentStatus = function() {
    var buttonProps = {
        action: function() {
            var view = this.view;
            var grid = view.viewGrid;
            var params = this.params;

            var selectedRecord = grid.getSelectedRecords();
            if (selectedRecord.length <= 0) {
                view.messageBar.setMessage('error', 'Document Routing', 'No record is selected.');
                return;
            }
            //params.button.contextView.viewGrid.getSelectedRecords(),
            var recordIdList = [],
                i, callback;

            var docstatus = [selectedRecord[0].documentStatus];
            callback = function(rpcResponse, data, rpcRequest) {
                if (data.enable == 1) {               
                    execute(view, view);
                } else {
                    view.messageBar.setMessage('error', 'Document Routing', data.message);
                }
            };
            var windowId = [view.windowId];
            var tabId = [view.tabId];
            var modew = ["checkWindowType"];
            OB.RemoteCallManager.call('org.wirabumi.gen.oez.event.DocRoutingToolbarHandler', {
                windowid: windowId,
                tabid: tabId,
                action: modew,
                docStatusFrom: docstatus
            }, {}, callback);
        },
        buttonType: 'oez_docrouting',
        prompt:'Approve',
        updateState: function() {

        }
    };
    // register the button for the sales order tab
    // the first parameter is a unique identification so that one button can not be registered multiple times.
    OB.ToolbarRegistry.registerButton(buttonProps.buttonType, isc.OBToolbarIconButton, buttonProps, 100, '');
};
cekDocumentStatus();
execute = function(params, view) {
	//selection = params.button.contextView.viewGrid.getSelectedRecords(),
			var i,selection = view.viewGrid.getSelectedRecords();			
            var recordIdList = [], callback, validationMessage, validationOK = true;
            var messageBar = view.messageBar;
                   		
        params.views = view;
        params.messageBars = view.messageBar;        
        callback = function(rpcResponse, data, rpcRequest) {
            var status = rpcResponse.status,
            view = rpcRequest.clientContext.view.getView(params.adTabId);
            view.messageBar.setMessage(data.message.severity, null, data.message.text);
            // close process to refresh current view
            view.button.closeProcessPopup();
        };
        for (i = 0; i < selection.length; i++) {
            recordIdList.push(selection[i].id);
        };

        // call the popup paramater
        isc.HrisDocRoutingProcessPopup.create({
            recordIdList: recordIdList,
            view: view,
            params: params
        }).show();
    };

DocRouting= function(params, view) {
    params.actionHandler = 'org.wirabumi.gen.oez.event.DocumentRouting';
    params.adTabId = view.activeView.tabId;
    params.adWindowId = view.windowId;
    params.processId = '2DF03BA5F81F48AA841D9F99D4E8333C';
    OB.OEZ.execute(params, view);
};

isc.defineClass('HrisDocRoutingProcessPopup', isc.OBPopup);

isc.HrisDocRoutingProcessPopup.addProperties({

    width: 320,
    height: 200,
    title: null,
    showMinimizeButton: false,
    showMaximizeButton: false,

    //Form
    mainform: null,

    //Button
    okButton: null,
    cancelButton: null,
    views:null,
    doc_status_from:null,
    getActionList: function(form) {
        var send = {
                recordIdList: this.recordIdList
            },
            actionField, popup = this;
        send.action = 'OpenPopupParamater';
        send.adTabId = this.params.tabId;
        send.windowId = this.params.windowId;
        send.views = this.views;
        send.doc_status_from = this.doc_status_from;        
        // Call The Handler
        OB.RemoteCallManager.call('org.wirabumi.gen.oez.event.DocumentRoutingHandler', send, {},
            function(response, data, request) {
                if (response) {
                    actionField = form.getField('Action');
                    var data = response.data;
                    if (response.data["message"] != null) {                        
                        var views = send.views;                        
                        views.messageBar.setMessage(data.message.severity, null, data.message.text);
                        popup.closeClick();
                        //	    	   this.originalView.refresh(false, false);
                    } else {
                        popup.setTitle('Process Request');
                        actionField.DocRoutingStepId = response.data.DocRoutingStepId;
                        actionField.setValueMap(response.data.actionComboBox.valueMap);
                        actionField.setDefaultValue(response.data.actionComboBox.defaultValue);
                    }
                }
            });
    },

    //define the popup inteface
    initWidget: function() {

        OB.TestRegistry.register('org.wirabumi.gen.oez.popup', this);
        var recordIdList = this.recordIdList,originalView = this.view,params = this.params;
        var grid=this.view.viewGrid;
        this.views=this.view;
        var selectedRec=grid.getSelectedRecords();
        this.doc_status_from='';        
        this.mainform = isc.DynamicForm.create({
            numCols: 2,
            colWidths: ['50%', '50%'],
            fields: [{
                name: 'Action',
                title: OB.I18N.getLabel('Action'),
                height: 20,
                width: 255,
                required: true,
                type: '_id_17',
                DocRoutingStepId: null,
                defaultToFirstOption: true
            }]
        });

        //== end dynamic create form

        this.okButton = isc.OBFormButton.create({
            title: OB.I18N.getLabel('OK'),
            popup: this,
            view:this.views,
            action: function() {
                var callback, action, adTabId, windowId;
                var paramsx=this.view;
                var popupx=this.popup;
                //popupx.closeClick();
                callback = function(rpcResponse, data, rpcRequest) {                	                    
                    var view = rpcRequest.clientContext.originalView;//getView(paramsx.tabId);                  
                    if (data.message) {
                        view.messageBar.setMessage(data.message.severity, null, data.message.text);
                    }
                    view.refresh(false, false);
                    popupx.closeClick();               
                };
                action = this.popup.mainform.getItem('Action').getValue();
                adTabId = params.tabId;
                windowId = params.windowId;  
                var doc_status_from=this.popup.doc_status_from;                
                OB.RemoteCallManager.call('org.wirabumi.gen.oez.event.DocumentRoutingHandler', {
                    DocRoutingStepId: this.popup.mainform.getItem('Action').DocRoutingStepId,
                    recordIdList: recordIdList,
                    action: action,
                    adTabId: adTabId,
                    windowId: windowId,                    
                    doc_status_from:''
                }, {}, callback, {
                    originalView: this.popup.view,
                    popup: this.popup
                });
            }
        }); //== end of oke button

        OB.TestRegistry.register('org.wirabumi.gen.oez.popup.okButton', this.okButton);
        this.cancelButton = isc.OBFormButton.create({
            title: OB.I18N.getLabel('Cancel'),
            popup: this,
            action: function() {
                this.popup.closeClick();
            }
        });

        this.getActionList(this.mainform);

        this.items = [
            isc.VLayout.create({
                defaultLayoutAlign: "center",
                align: "center",
                width: "100%",
                layoutMargin: 10,
                membersMargin: 6,
                members: [
                    isc.HLayout.create({
                        defaultLayoutAlign: "center",
                        align: "center",
                        layoutMargin: 30,
                        membersMargin: 6,
                        members: this.mainform
                    }),
                    isc.HLayout.create({
                        defaultLayoutAlign: "center",
                        align: "center",
                        membersMargin: 10,
                        members: [this.okButton, this.cancelButton]
                    })
                ]
            })
        ];
        this.Super('initWidget', arguments);
    }
});