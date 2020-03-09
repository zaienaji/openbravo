// this Document Was Created By Wirabumi SoftWare
// author Rizal 

(function () {
  var buttonUpdated = {
    action: function () {
      var callback, RecordId = [],
          i, view = this.view,me=this,
          grid = view.viewGrid,
          selectedRecords = grid.getSelectedRecords(),tab=view.tabId;
      // ambil id record
      for (i = 0; i < selectedRecords.length; i++) {
    	  RecordId.push(selectedRecords[i][OB.Constants.ID]);
      }

      isc.FormUpdate.create({
	      view: view,
	      tabID :tab,
	      id:RecordId,
	}).show();
    },
    buttonType: 'OEZ_Updated',
    prompt: "Update Button"
  };

  // kosongi parameter tarakhir agar semua tab muncul
 OB.ToolbarRegistry.registerButton(buttonUpdated.buttonType, isc.OBToolbarIconButton, buttonUpdated, 100, '');
}());

//definisi form

isc.defineClass('FormUpdate',isc.OBPopup);

isc.FormUpdate.addProperties({
	width: 320,
	  height: 100,
	  title: "Update Data",
	  showMinimizeButton: false,
	  showMaximizeButton: false,
	  initWidget: function(){
		  var me=this;		  
		    // Form		  
		    this.mainform = isc.DynamicForm.create({
		      fields: [
               {
				 name: 'entity',
			     title: 'Entiny',
				 height: 20,
				 width: 200,
				 type: '_id_10',
				 defaultToFirstOption:true
			  },{
			    name: 'value',
		        title: 'Value',
			    height: 20,
			    width: 200,
			    type: '_id_10',
			    defaultToFirstOption:true
			    
			  }
		      ],wrapItemTitles:false
		   });
			
		    // OK Button
		    this.okButton = isc.OBFormButton.create({
		      title: ('Submit'),
		      popup: this,
		      click: function () {
		    	  isc
					.showPrompt(OB.I18N
							.getLabel('OBUIAPP_PROCESSING')
							+ isc.Canvas
									.imgHTML({
										src : OB.Styles.LoadingPrompt.loadingImage.src
									}));
		        var callback, i,params,View,popupx=this.popup;

		        callback= function(rpcResponse, data, rpcRequest){
		        	var view = rpcRequest.clientContext.originalView;
		        	view.messageBar.setMessage(data.severity, data.title,data.text);
		        	isc.clearPrompt();
					view.refresh(false, false);
					popupx.close();
				};
				var entity=me.mainform.getItem('entity').getValue(),
					value=me.mainform.getItem('value').getValue();
				
				OB.RemoteCallManager.call('org.wirabumi.gen.oez.event.UpdateData', {
				      idrec:me.id,
				      entity:entity,
				      value:value,
				      tab:this.popup.tabID
				}, {}, callback,{originalView: me.view});
				
		      }
		   });
		   
		   // Cancel Button
		   this.cancelButton = isc.OBFormButton.create({
		     title:('Cancel'),
		     popup: this,
		     action: function () {
		    	 this.popup.closeClick();
		     }
		   }); 
		   
		      
		   this.items = [
		     isc.VLayout.create({
		       defaultLayoutAlign: "center",
		       align: "center",
		       width: "100%",
		       layoutMargin: 10,
		       membersMargin: 1,
		       members: [
		                 isc.HLayout.create({
		                 defaultLayoutAlign: "center",
		                 align: "left",
		                 layoutMargin: 1,
		                 membersMargin: 6,
		                 members: this.mainform
		                 }),
		                 isc.HLayout.create({
		                 defaultLayoutAlign: "center",
		                 align: "center",
		                 layoutMargin: 5,
		                 membersMargin: 10,
		                 members: [this.okButton, this.cancelButton]
		                 })
		                 ]
		     })
		   ];
		    this.Super('initWidget', arguments);
	  }
});