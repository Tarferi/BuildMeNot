
var terminer = function() {
    var self = this
    self.common = new Common();
	self.root = document.createElement("div");
	self.root.style.position = "relative";
	self.root.style.top = "80px";
    
    self.getDate = function(stamp) {
    	var dd = function(i) {
    	   if(i < 9) {
    	      return "0"+i
    	   } else {
    	      return "" + i;
    	   }
    	}
		var cas = new Date(stamp);
		cas = cas.getDate() + "." + (cas.getMonth()+1) + "." + cas.getFullYear() + " " + dd(cas.getHours()) + ":" + dd(cas.getMinutes());
		return cas
    }

    self.materialize = function(data) {
    	var available = data.Available;
    	var my = data.MyData
    	self.root.innerHTML = "";
    	
    	if(data.Now && data.NextEvent) {
    		var diff = data.NextEvent - data.Now;
    		if(diff > 0) {
    		    setTimeout(function(){ self.loadTerms(true, false); }, diff+10000);
    		}
    	}
    	
    	var newMy = {};
    	for(var m in my) {
    	   if(my.hasOwnProperty(m)) {
    	      var d = my[m]
    	      var cas = self.getDate(d.Time);
    	      d.Time = cas;
    	      newMy[d.SlotID] = d;
    	   }
    	}
    	
    	var adm = {};
    	var allowedLogins = {};
    	if(data.Admin && data.AdminAll) {
	    	for(var m in data.AdminAll) {
	    		if(data.AdminAll.hasOwnProperty(m)) {
	    			for(var i = 0; i < data.AdminAll[m].length; i++) {
	    			    var am = data.AdminAll[m][i];
	    			    allowedLogins[am.Login] = true;
						if(am.SlotID in adm) {
							adm[am.SlotID][am.Login] = am;
						} else {
							adm[am.SlotID] = {};
				    		adm[am.SlotID][am.Login] = am;
						}
					}
				}
	    	}
			for(var i = 0; i < data.Admin.length; i++) {
				var am = data.Admin[i];
				if(am.Login in allowedLogins) {
					if(am.SlotID in adm) {
						adm[am.SlotID][am.Login] = am;
					} else {
						adm[am.SlotID] = {};
				    	adm[am.SlotID][am.Login] = am;
					}
				}
			}    	
    	} 
    	var cdm = {}
		for(var m in adm) {
			if(adm.hasOwnProperty(m)) {
				cdm[m] = [];
				for(var mm in adm[m]) {
					if(adm[m].hasOwnProperty(mm)) {
						cdm[m].push(adm[m][mm]);
					}	
				}		
			}
		}
		adm = cdm;
    	
    	for(var i = 0; i < available.length; i++) {
    	   var av = available[i];
    	   var show = (!data.Admin) || (!data.AdminAll); // Pokud nejsou admini
    	   if(av.OwnerLogin == "") {
    	   	   show = true;
    	   } else if(av.OwnerLogin == self.common.identity.login) {
    	   		show = true;
    	   } else {
    	   		show |= av.OwnerLogin.split(",").reduce(function(data, item) {return item==self.common.identity.login||data;}, false);
    	   }
    	   if(show) {
    	   	   var el = self.constructTerm(av, newMy, adm);
    	   	   self.root.appendChild(el);
	   	   }
	   }
	   return;
    }
    
    self.changeOption = function(slotID, variantID) {
		self.common.showLoader();
    	var cbFail = function(data) {
    		self.common.hideLoader();
			var el = document.createElement("span");
			el.innerHTML = data;
			alert(el.innerText);
		};
		var cbOk = function(data) {
			self.common.hideLoader();
			if(data && data.MyData && data.Available) {
				self.materialize(data);
				self.common.setLoginPanelVisible(true);
			} else {
				cbFail("Nepodařilo se nahrát termíny kvůli špatné struktuře ze serveru");
			}
		};
		var data = {"action":"HANDLE_TERMS", "term_data": "subscribe", "slotID": slotID, "variantID": variantID};
		self.common.async(data, cbOk, cbFail);
    
    }
    
    self.signUp = function(slotID, variantID) {
    	self.changeOption(slotID, variantID);
    }
    
    self.btnHideCB = function(id, btn, btnRefresh, el1, el2, spanner) {
    	if(btn.innerHTML == "Skrýt") {
    	   self.setHidden(id, true);
    	   btn.innerHTML = "Zobrazit";
    	   el2.style.display ="none";
    	   el1.style.borderBottom="none";
    	   btnRefresh.style.display = "none";
    	   spanner.style.marginBottom = "10px";
    	} else {
    	   self.setHidden(id, false);
    	   btn.innerHTML = "Skrýt";
    	   el2.style.display ="";
    	   el1.style.borderBottom="";
    	   btnRefresh.style.display = "";
			spanner.style.marginBottom = "70px";
    	}
    }
    
    self.constructTerm = function(data, my, adm) {
        var templates = new UITemplates();

       	var struct = self.common.reconstructUI(templates.UI);
		var el = struct[0];
		var ids = struct[1];
		
		var loggedVariant = false;
		
		var constrF = function(st, labels) {
			var name = st.Name;
		    var code = st.Code;
		    var limit = st.Limit;
		    var value = st.Value;
		      
		      
		    var subStruct = self.common.reconstructUI(templates.signUI);
		    var subEls = subStruct[0];
		    var subIds = subStruct[1];
		      
		    subIds.cellCap.innerHTML = limit;
		    subIds.cellName.innerHTML = name;
		    subIds.cellNow.innerHTML = value;
		    
		    var prihlasenLbl = labels.SelfSignedUp ? labels.SelfSignedUp : "Přihlášen";
		    var prihlasitLbl = labels.SignUp ? labels.SignUp : "Přihlásit";
 		    var odhlasitLbl = labels.SignOut ? labels.SignOut : "Odhlásit";
		    
		    if(data.Available == 1) {
			    if(data.ID in my && my[data.ID].Type == code) {
			        var cas =  my[data.ID].Time;
				    subIds.btnLog.addEventListener("click", function() {self.signUp(data.ID, data.DefaultType);});
				    subIds.btnLog.innerHTML = odhlasitLbl;
				    subIds.term_table_logged.innerHTML = prihlasenLbl + ": " +cas;
			    } else {
				    subIds.btnLog.addEventListener("click", function() {self.signUp(data.ID, code);});
				    subIds.btnLog.innerHTML = prihlasitLbl;
				    subIds.term_table_logged.style.display = "none";
	            }
            } else {
            	subIds.btnLog.style.display = "none";
            	if(data.ID in my && my[data.ID].Type == code) {
            		var cas =  my[data.ID].Time;
            		subIds.term_table_logged.innerHTML = prihlasenLbl + ": " +cas;
        		}
            }
            
            for(var lbl in labels) {
                if(labels.hasOwnProperty(lbl) && ids.hasOwnProperty("lbl_"+lbl)) {
                    var id = ids["lbl_"+lbl];
                    id.innerHTML = labels[lbl];
                }
            }
            
            return subEls;
		};
		
		if(data.ID in my && my[data.ID].Show) {
	    	loggedVariant = "Přihlášena varianta \""+my[data.ID].TypeName+"\"";
	    	ids.nwBorder.style.background = "#68CD34";
    	} 
		
		for(var d in data.Stats) {
		   if(data.Stats.hasOwnProperty(d)) {
		      var st = data.Stats[d];
		      if(st.Show) {
		         ids.term_loginTable.appendChild(constrF(st, data.Labels ? data.Labels : {}));
		      }
		   }
		}
		if(data.ID in adm && adm[data.ID].length && adm[data.ID].length > 0) {
			var admData = adm[data.ID];
			// Resort by types
			var newAdmData = {};
			for(var i = 0; i < admData.length;i++) {
				var type = admData[i].Type;
				if(!(type in newAdmData)) {
					newAdmData[type] = [];					
				}
				newAdmData[type].push(admData[i]);
			}
			admDataTotal = newAdmData;
			
			for(var admDataTotalKey in admDataTotal) {
			   if(admDataTotal.hasOwnProperty(admDataTotalKey)) {
				   var admData = admDataTotal[admDataTotalKey];
				   if(admData.length > 0) {
						
				      	var admStruct = self.common.reconstructUI(templates.admUITable);
						var admEl = admStruct[0];
						var admIds = admStruct[1];
					
					
						admIds.term_adm_var.innerHTML = "Varianta \""+admData[0].TypeName+"\"";
						admData.sort(function(a, b) {return (a.Login > b.Login) ? 1 : -1;})
					
						for(var i = 0; i < admData.length; i++) {
						    var aData = admData[i];
						   
					      	var aDataStruct = self.common.reconstructUI(templates.admUI);
							var aDataEl = aDataStruct[0];
							var aDataIds = aDataStruct[1];
							var cas = "";
							if(aData.Time && aData.Time > 0) {
								cas = self.getDate(aData.Time);
			    	      	}
							
							aDataIds.cellOrder.innerHTML = (i+1)+"";
							aDataIds.cellTime.innerHTML = cas;
							aDataIds.cellName.innerHTML = aData.Name
							aDataIds.cellLogin.innerHTML = aData.Login;
							
						    admEl.appendChild(aDataEl);
						}
						ids.pre_adm_appender.appendChild(document.createElement("br"));
						ids.pre_adm_appender.appendChild(admEl);
					}
				}
			}
		}
		
		ids.txtBrief.innerHTML = data.Title;
		ids.txtDescr.innerHTML = data.Description.split("\n").join("<br />");
		var hideCB =  function() {self.btnHideCB(data.ID, ids.btnHide, ids.btnRefresh, ids.nwBorder, ids.pnlMain, ids.term_table_bottom_spanner);}
		ids.btnHide.addEventListener("click", hideCB);
		if(self.wasHidden(data.ID)) {
			hideCB();
		}
		
		ids.btnRefresh.addEventListener("click", function() {self.loadTerms(true, false);});
		
		if(loggedVariant !== false) {
			ids.txtBrief.innerHTML += " ("+loggedVariant+")";		
		}
		
		return el;
    };
    
    self.setHidden = function(slotID, collapsed) {
    	slotID = "slot_"+slotID;
  		if(window.location && window.location.href && window.localStorage && window.localStorage.getItem) {
    		var key = window.location.href+":collapsed";
    	   	var data = window.localStorage.getItem(key);
    	   	var newData = {};
    	   	if(data) {
    	   		data = JSON.parse(data);
    	   		if(data) {
    	   			newData = data;
    	   		}
    	   	}
    	   	newData[slotID] = collapsed;
    	   	window.localStorage.setItem(key, JSON.stringify(newData));
    	}
    }
    
    self.wasHidden = function(slotID) {
    	slotID = "slot_"+slotID;
    	if(window.location && window.location.href && window.localStorage && window.localStorage.getItem) {
    		var key = window.location.href+":collapsed";
    	   	var data = window.localStorage.getItem(key);
    	   	if(data) {
    	   		data = JSON.parse(data);
    	   		if(data) {
    	   			if(slotID in data) {
    	   				return data[slotID];
    	   			}
    	   		}
    	   	}
    	}
    	return false;
    }
	
    self.loadTerms = function(useWaiter, useInitWaiter) {
    	if(useWaiter) {
    		self.common.showLoader();
    	} else if(useInitWaiter) {
			self.showInitLoader();
		}

   		var cbFail = function(data) {
   			self.common.hideLoader();
			self.hideInitLoad();
    		self.root.innerHTML = "";
			if(useInitWaiter) {
				self.showFailedLoad(data);
			}
		};
		var cbOk = function(data) {
			self.common.hideLoader();
			self.hideInitLoad();
			self.root.innerHTML = "";
			if(data && data.MyData && data.Available) {
				self.materialize(data);
				self.common.setLoginPanelVisible(true);
			} else {
				cbFail("Nepodařilo se nahrát termíny kvůli špatné struktuře ze serveru");
			}
		};
		var data = {"action":"HANDLE_TERMS", "term_data": "getTerms"};
		self.common.async(data, cbOk, cbFail);
    }

	self.showInitLoader = function() {
		self.common.showInitLoader("Nahrávám seznam termínů...", "green");
	}
	
	self.showFailedLoad = function(descr) {
		var df = "Nepodařilo se nahrát seznam termínů"
		if (descr !== undefined) {
			df = df+":<br />"+descr;
		}
		self.common.showInitLoader(df, "red");
	}
	
	self.hideInitLoad = function() {
		self.common.hideInitLoader();
	}
    
	self.aload = function() {
		document.body.appendChild(self.root);
		self.common.setLoginPanelVisible(true);
		self.showInitLoader();
 	    self.loadTerms(false, true);
	}
}

function cviceni_load() {
   window.terminer = new terminer();
   terminer.aload();
	if(window.pastAload){
		window.pastAload();
	}
}

$INJECT(cviceni/templates.js)$
$INJECT(common.js)$
$INJECT(WEB.ADMIN, admin.js)$