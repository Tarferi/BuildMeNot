$INJECT(common.js)$

function ExamAdminNoAccess() {
	self.common.showError("Chyba", "Nemáte dostatečná práva k přístupu k tomuto dokumentu", false);
}

$INJECT_CODE_NOPERMS(WEB.EXAMS.ADMIN, window.adm_load = ExamAdminNoAccess;)$



var ExamAdminEditableField = function(data, id, viewNode, editableNode, fmt) {
	var self = this;
	self.viewNode = viewNode;
	self.editNode = editableNode;
	self.ID = id;
	self.fmt = fmt;
	var defaultData = "";
	var outFormat = new CommonFormats();

	defaultData = fmt.getDefaultValue(data, id);

	
	self.formatNone = function(data) {
		return data;
	}
	
	self.viewValueMemberName = "innerHTML"
	self.viewValueFormat = outFormat.format;
	if(viewNode.tagName.toLowerCase() == "input"){
		self.viewValueFormat = self.formatNone;
		self.viewValueMemberName = "value"
		if(viewNode.type == "checkbox") {
			self.viewValueMemberName = "checked"
		}
	}


	self.valueMemberName = editableNode.type == "checkbox" ? "checked" : "value"
	
	self.setEditingEnabled = function(enabled) {
		self.viewNode.style.display = enabled ? "none" : "";
		self.editNode.style.display = enabled ? "" : "none";
		if (enabled) {
			self.editNode[self.valueMemberName] = defaultData;
		}
	}

	self.init = function() {
		self.setEditingEnabled(false);
		viewNode[self.viewValueMemberName] =  self.viewValueFormat(fmt.format(data, id));
	};

	self.getEditValue = function(data) {
		var value = self.editNode[self.valueMemberName]
		return fmt.parse(data, id, value);
	}

	this.init();
	return this;
};

var ExamAdminGroup = function(data, stopGroupEditing, saveCB, delCB) {
	var self = this;
	this.ID = data.ID;
	this.data = data;
	self.node = null;
	self.fields = [];
	var templates = new ExamsAdminUITemplates();
	var formats = new ExamsAdminFormats();
	self.common = new Common();

	self.stopGroupEditing = stopGroupEditing;

	self.init = function() {
		self.materialize();
	}

	self.getEditData = function() {
		var newData = self.data;
		for (var i = 0; i < self.fields.length; i++) {
			newData = self.fields[i].getEditValue(newData);
		}
		return newData;
	}

	self.setEditingEnabled = function(enabled) {
		for (var i = 0; i < self.fields.length; i++) {
			self.fields[i].setEditingEnabled(enabled);
		}
		self.btn_edit.style.display = enabled ? "none" : "";
		self.btn_save.style.display = enabled ? "" : "none";
		self.btn_cancel.style.display = enabled ? "" : "none";
		self.btn_del.style.display = enabled ? "none" : "";
	}

	self.save = function() {
		var data = self.getEditData();
		saveCB(data);
	}

	self.beginEdit = function() {
		self.stopGroupEditing();
		self.setEditingEnabled(true);
	}

	self.materialize = function() {
		if (self.node == null) {
			var textFmt = new formats.TextFmt();
			var intFmt = new formats.IntFmt();
			var listFmt = new formats.ListFmt(intFmt);

			var data = self.common.reconstructUI(templates.examGroupRowUI);
			self.node = data[0];
			var ids = data[1];

			self.fields.push(new ExamAdminEditableField(self.data, "name", ids.set_view_name, ids.set_edit_name_editor, textFmt));
			self.fields.push(new ExamAdminEditableField(self.data, "config.questions", ids.set_view_qlist, ids.set_edit_qlist_editor, listFmt));
			self.fields.push(new ExamAdminEditableField(self.data, "config.evaluation", ids.set_eval, ids.edit_eval, intFmt));

			ids.pnl_id.innerHTML = self.data.ID;

			self.btn_edit = ids.btn_edit;
			self.btn_save = ids.btn_save;
			self.btn_cancel = ids.btn_cancel;
			self.btn_del = ids.btn_del;

			self.btn_edit.addEventListener("click", function() {
				self.beginEdit();
			});
			self.btn_cancel.addEventListener("click", function() {
				self.stopGroupEditing();
			});
			self.btn_save.addEventListener("click", function() {
				self.save();
			});
			self.btn_del.addEventListener("click", function() {
				delCB(self.data);
			});
			self.setEditingEnabled(false);

		}
	}

	this.init();
	return this;
};

var ExamAdminQuestion = function(data, types, stopQuestionEditing, saveCB, delCB) {
	
	var self = this;
	this.ID = data.ID;
	this.data = data;
	self.types = types;
	self.node = null;
	self.fields = [];
	var templates = new ExamsAdminUITemplates();
	var formats = new ExamsAdminFormats();
	self.common = new Common();
	
	var editTypeChangeCB = null;

	self.stopQuestionEditing = function() {
		if (editTypeChangeCB !== null) {
			editTypeChangeCB(self.data.config.type);
		}
		stopQuestionEditing();
	};

	self.init = function() {
		self.materialize();
	}

	self.getEditData = function() {
		var newData = self.data;
		for (var i = 0; i < self.fields.length; i++) {
			newData = self.fields[i].getEditValue(newData);
		}
		return newData;
	}

	self.setEditingEnabled = function(enabled) {
		for (var i = 0; i < self.fields.length; i++) {
			self.fields[i].setEditingEnabled(enabled);
		}
		self.btn_edit.style.display = enabled ? "none" : "";
		self.btn_save.style.display = enabled ? "" : "none";
		self.btn_cancel.style.display = enabled ? "" : "none";
		self.btn_del.style.display = enabled ? "none" : "";
	}

	self.save = function() {
		var data = self.getEditData();
		saveCB(data);
	}

	self.beginEdit = function() {
		self.stopQuestionEditing();
		self.setEditingEnabled(true);
	}

	self.materializeQuestionTypes = function(node) {
		if (self.types && self.types && self.types.length) {
			for (var i = 0; i < self.types.length; i++) {
				var obj = self.types[i];
				if (obj && obj.ID !== undefined && obj.name) {
					var el = document.createElement("option");
					el.innerHTML = obj.name;
					el.value = obj.ID;
					node.appendChild(el);
				}
			}
		}
	}

	self.materialize = function() {
		if (self.node == null) {
			var textFmt = new formats.TextFmt();
			var listFmt = new formats.ListFmt();
			var textListFmt = new formats.ListFmt();
			var intFmt = new formats.IntFmt();
			var boolFmt = new formats.BoolFmt();

			var types = [];
			if (self.types && self.types && self.types.length) {
				for (var i = 0; i < self.types.length; i++) {
					var obj = self.types[i];
					if (obj && obj.ID !== undefined && obj.name) {
						types[obj.ID] = obj.name
					}
				}
			}

			var intEnumFmt = new formats.IntEnumFmt(types);

			var data = self.common.reconstructUI(templates.examQuestionRowUI);
			self.node = data[0];
			var ids = data[1];

			self.materializeQuestionTypes(ids.edit_type_editor);

			self.fields.push(new ExamAdminEditableField(self.data, "name", ids.set_view_name, ids.set_edit_name_editor, textFmt));
			self.fields.push(new ExamAdminEditableField(self.data, "config.description",ids.set_view_descr, ids.set_edit_descr_editor, textFmt));
			self.fields.push(new ExamAdminEditableField(self.data, "config.type", ids.set_edit_type, ids.edit_type_editor, intEnumFmt));
			self.fields.push(new ExamAdminEditableField(self.data, "config.options",	ids.set_edit_opt, ids.edit_opt_editor, textListFmt));
			self.fields.push(new ExamAdminEditableField(self.data, "config.multiple", ids.set_edit_opt2, ids.edit_opt2_editor, boolFmt));

			editTypeChangeCB = function(val) {
				if (val >= 0 && val <= 1) {
					ids.row_edit_opt.style.display = "";
					ids.row_edit_opt2.style.display = "";
				} else {
					ids.row_edit_opt.style.display = "none";
					ids.row_edit_opt2.style.display = "none";
				}
			}

			ids.edit_type_editor.addEventListener("change", function() {
				editTypeChangeCB(ids.edit_type_editor.value);
			});
			editTypeChangeCB(self.data.config.type);

			ids.pnl_id.innerHTML = self.data.ID;

			self.btn_edit = ids.btn_edit;
			self.btn_save = ids.btn_save;
			self.btn_cancel = ids.btn_cancel;
			self.btn_del = ids.btn_del;

			self.btn_edit.addEventListener("click", function() {
				self.beginEdit();
			});
			self.btn_cancel.addEventListener("click", function() {
				self.stopQuestionEditing();
			});
			self.btn_save.addEventListener("click", function() {
				self.save();
			});
			self.btn_del.addEventListener("click", function() {
				delCB(self.data);
			});
			self.setEditingEnabled(false);

		}
	}

	this.init();
	return this;
};

var ExamAdminOpenedTest = function(data, closeEverythingElseCB, closeCB) {
	var self = this;
	self.ID = data.ID;
	self.common = new Common();
	
	self.materialize = function(hasFrameReady) {
		if(hasFrameReady !== true) {
			closeEverythingElseCB();
		}
		
		self.examer = new ExamExamer(false);
		self.common.showLoader();
		var exData = {
			 "name": data.FullName, 
			 "close": self.close,
			 "save": self.save
		}
		self.examer.loadExam(data.ID, exData);
	}

	self.close = function(keepUIFrame) {
		// close
		document.body.removeChild(self.examer.root);
		if(keepUIFrame !== true) {
			closeCB();
		}
	}

	self.save = function(data) {
		self.common.showLoader();
		var cbOK = function() {
			self.common.hideLoader();
			self.close(true);
			self.materialize(true);
		}
		var cbFail = function(err) {
			self.common.hideLoader();
			self.common.showError("Chyba při ukládání hodnocení", "Nepodařilo se uložit hodnocení", true, err);
		}
		var asyncData = {
			"action" : "HANDLE_EXAMS",
			"exam_data" : "eval_results",
			"ID": self.ID,
			"data" : data
		};
		self.common.showLoader();
		self.common.async(asyncData, cbOK, cbFail, false);
	}
	
	self.init = function() {
		self.materialize();
	}
	
	self.init();
	return this;
}

var ExamAdminExam = function(data, stopExamEditing, saveCB, delCB, setRenderedTablesVisibleCB, mainReloadCB) {
	var self = this;
	this.ID = data.ID;
	self.data = data;
	self.node = null;
	var templates = new ExamsAdminUITemplates();
	var formats = new ExamsAdminFormats();
	self.common = new Common();
	self.fields = [];
	self.btn_edit = null;
	self.btn_save = null;
	self.btn_cancel = null;
	self.btn_del = null;
	self.setRenderedTablesVisibleCB = setRenderedTablesVisibleCB;
	self.mainReloadCB = mainReloadCB;

	self.stopExamEditing = stopExamEditing;

	self.setEditingEnabled = function(enabled) {
		for (var i = 0; i < self.fields.length; i++) {
			self.fields[i].setEditingEnabled(enabled);
		}
		self.btn_edit.style.display = enabled ? "none" : "";
		self.btn_save.style.display = enabled ? "" : "none";
		self.btn_cancel.style.display = enabled ? "" : "none";
		self.btn_del.style.display = enabled ? "none" : "";
	}

	self.getEditData = function() {
		var newData = self.data;
		for (var i = 0; i < self.fields.length; i++) {
			newData = self.fields[i].getEditValue(newData);
		}
		return newData;
	}

	self.resultsPnl = undefined;
	self.materializeShownResults = function(data) {
		self.common.hideLoader();
				
		self.setRenderedTablesVisibleCB(false);
		self.resultsPnl = document.createElement("div");
		
		var d = self.common.reconstructUI(templates.usersHeaderUI);
		var node = d[0];
		var ids = d[1];
		
		
		var evaluated = [];
		var finished = [];
		var timeouted = [];
		var running = [];
		var others = [];
		
		for(var i = 0; i < data.length; i++) {
			var item = data[i];
			if(item.Evaluated) {
				evaluated.push(item);
			} else if(item.Finished) {
				finished.push(item);
			} else if (item.Timeouted) {
				timeouted.push(item);
			} else if(item.StartedAt) {
				running.push(item);
 			} else {
				others.push(item);
			}
		}
		
		var materializeRow = function(data, index) {
			self.common.hideLoader();
			var d = self.common.reconstructUI(templates.usersRowUI);
			var node = d[0];
			var ids = d[1];
			
			ids.pnl_id.innerHTML = data.ID;
			ids.pnl_login.innerHTML = data.Login;
			
			var status = "";
			
			var details1 = [];
			var details2 = [];
			
			if(data.Evaluated) {
				status = "Ohodnoceno";
				details1.push("Zahájeno");
				details2.push(self.common.convertDateTime(data.StartedAt));
				if(data.FinishedAt){
					details1.push("Odevzdáno");
					details2.push(self.common.convertDateTime(data.FinishedAt));
				}
				details1.push("Ohodnoceno");
				details2.push(self.common.convertDateTime(data.EvaluatedAt));		
			} else if(data.Finished) {
				status = "Odevzdáno";
				details1.push("Zahájeno");
				details2.push(self.common.convertDateTime(data.StartedAt));
				details1.push("Odevzdáno");
				details2.push(self.common.convertDateTime(data.FinishedAt));
			} else if (data.Timeouted) {
				status = "Ukončeno vypršením času";
				details1.push("Zahájeno");
				details2.push(self.common.convertDateTime(data.StartedAt));
				details1.push("Vypršení času");
				details2.push(self.common.convertDateTime(data.TimeoutedAt));
			} else if(data.StartedAt) {
				status = "Probíhá";
				details1.push("Zahájeno");
				details2.push(self.common.convertDateTime(data.StartedAt));
 			} else {
				status = "Nezahájeno";
			}
			
			ids.pnl_status.innerHTML = status;
			ids.pnl_details1.innerHTML = details1.join("<br />");
			ids.pnl_details2.innerHTML = details2.join("<br />");
			
			ids.btn_open.addEventListener("click", function() {
				new ExamAdminOpenedTest(data, function() {
					self.resultsPnl.style.display = "none";
				}, function() {
					self.resultsPnl.style.display = "";
				})
			});
			
			return node;
		}
		
		var appendMaterializedNode = function(node) {
			ids.pnl_body.appendChild(node);
			return node;
		}
		
		evaluated.map(materializeRow).map(appendMaterializedNode);
		finished.map(materializeRow).map(appendMaterializedNode);
		timeouted.map(materializeRow).map(appendMaterializedNode);
		running.map(materializeRow).map(appendMaterializedNode);
		others.map(materializeRow).map(appendMaterializedNode);

		ids.ex_back.addEventListener("click", self.closeResults);
		ids.ex_back2.addEventListener("click", self.closeResults);
		
		var update = function() {
			self.closeResults();
			self.showResults();
		}
		
		ids.ex_update.addEventListener("click", update);
		ids.ex_update2.addEventListener("click", update);
		
		self.resultsPnl.appendChild(node);
		document.body.appendChild(self.resultsPnl);

	}
	
	self.showResults = function() {
		if(self.resultsPnl === undefined) {
			var asyncData = {
				"action" : "HANDLE_EXAMS",
				"exam_data" : "get_results",
				"ID" : self.ID
			};
			var cbFail = function(err) {
				self.common.hideLoader();
				self.common.showError("Chyba", "Nepodařilo se načíst výsledeky", true, err);
			}
			self.common.showLoader();
			self.common.async(asyncData, self.materializeShownResults, cbFail);
		}
	}
	
	self.closeResults = function() {
		if(self.resultsPnl !== undefined) {
			self.setRenderedTablesVisibleCB(true);
			document.body.removeChild(self.resultsPnl);
			self.resultsPnl = undefined;
		}
	}

	self.save = function() {
		var newData = self.getEditData();
		saveCB(newData);
	}

	self.beginEdit = function() {
		self.stopExamEditing();
		self.setEditingEnabled(true);
	}

	self.materialize = function() {
		if (self.node == null) {
			var textFmt = new formats.TextFmt();
			var intFmt = new formats.IntFmt();
			var listFmt = new formats.ListFmt(intFmt);
			var dateFmt = new formats.DateFmt();
			var timeFmt = new formats.TimeFmt();

			var data = self.common.reconstructUI(templates.examManagerTableRowUI);
			self.node = data[0];
			var ids = data[1];
			self.fields.push(new ExamAdminEditableField(self.data, "name", ids.set_view_name, ids.set_edit_name_editor, textFmt));
			self.fields.push(new ExamAdminEditableField(self.data, "config.groups", ids.set_view_groups, ids.set_edit_groups_editor, listFmt));
			self.fields.push(new ExamAdminEditableField(self.data, "config.visible_since", ids.set_view_see_since, ids.see_since, dateFmt));
			self.fields.push(new ExamAdminEditableField(self.data, "config.visible_until", ids.set_view_see_until, ids.see_until, dateFmt));
			self.fields.push(new ExamAdminEditableField(self.data, "config.turn_since", ids.set_view_save_since, ids.save_since, dateFmt));
			self.fields.push(new ExamAdminEditableField(self.data, "config.turn_until", ids.set_view_save_until, ids.save_until, dateFmt));
			self.fields.push(new ExamAdminEditableField(self.data, "config.exam_time", ids.set_view_ex_length, ids.edit_ex_length, timeFmt));

			self.btn_edit = ids.btn_edit;
			self.btn_save = ids.btn_save;
			self.btn_cancel = ids.btn_cancel;
			self.btn_del = ids.btn_del;
			self.btn_results = ids.ex_results;

			ids.pnl_id.innerHTML = self.data.ID;

			self.btn_edit.addEventListener("click", function() {
				self.beginEdit();
			});
			self.btn_cancel.addEventListener("click", function() {
				self.stopExamEditing();
			});
			self.btn_save.addEventListener("click", function() {
				self.save();
			});
			self.btn_del.addEventListener("click", function() {
				delCB(self.data);
			});
			self.btn_results.addEventListener("click", function() {
				self.showResults();
			});
			self.setEditingEnabled(false);
		}
	}

	self.init = function() {
		self.materialize();
	}

	this.init();
	return this;
}

var ExamAdminAdminer = function() {
	var self = this;
	self.exams = [];
	self.groups = [];
	self.questions = [];
	self.root = document.createElement("div");
	self.common = new Common();

	self.editGroup = function(data) {
		var cbOK = function(data) {
			self.common.hideLoader();
			self.reload();
		};
		var cbFail = function(err) {
			self.common.hideLoader();
			self.common.showError("Chyba", "Nepodařilo se upravit skupinu", true, err);
		}
		var asyncData = {
			"action" : "HANDLE_EXAMS",
			"exam_data" : "edit_group",
			"data" : data
		};
		self.common.showLoader();
		self.common.async(asyncData, cbOK, cbFail, false);
	}

	self.delGroup = function(data) {
		if (confirm('Opravdu smazat skupinu otázek "' + data.name + ' (' + data.ID + ')" ?')) {
			var asyncData = {
				"action" : "HANDLE_EXAMS",
				"exam_data" : "del_group",
				"data" : data
			};
			var cbFail = function(err) {
				self.common.hideLoader();
				self.common.showError("Chyba", "Nepodařilo se smazat skupinu", true, err).then(self.reload, self.reload);
			}
			self.common.showLoader();
			self.common.async(asyncData, self.reload, cbFail, false);
		}
	}

	self.editExam = function(data) {
		var asyncData = {
			"action" : "HANDLE_EXAMS",
			"exam_data" : "edit_exam",
			"data" : data
		};
		var cbFail = function(err) {
			self.common.hideLoader();
			self.common.showError("Chyba", "Nepodařilo se editovat termín", true, err).then(self.reload, self.reload);
		}
		self.common.showLoader();
		self.common.async(asyncData, self.reload, cbFail, false);
	}

	self.delExam = function(data) {
		if (confirm('Opravdu smazat termín "' + data.name + ' (' + data.ID + ')" ?')) {
			var asyncData = {
				"action" : "HANDLE_EXAMS",
				"exam_data" : "del_exam",
				"data" : data
			};
			var cbFail = function(err) {
				self.common.hideLoader();
				self.common.showError("Chyba", "Nepodařilo se smazat termín", true, err).then(self.reload, self.reload);
			}
			self.common.showLoader();
			self.common.async(asyncData, self.reload, cbFail, false);
		}
	}

	self.editQuestion = function(data) {
		var asyncData = {
			"action" : "HANDLE_EXAMS",
			"exam_data" : "edit_question",
			"data" : data
		};
		var cbFail = function(err) {
			self.common.hideLoader();
			self.common.showError("Chyba", "Nepodařilo se editovat otázku", true, err).then(self.reload, self.reload);
		}
		self.common.showLoader();
		self.common.async(asyncData, self.reload, cbFail, false);
	}

	self.delQuestion = function(data) {
		if (confirm('Opravdu smazat skupinu otázek "' + data.name + ' (' + data.ID + ')" ?')) {
			var asyncData = {
				"action" : "HANDLE_EXAMS",
				"exam_data" : "del_question",
				"data" : data
			};
			var cbFail = function(err) {
				self.common.hideLoader();
				self.common.showError("Chyba", "Nepodařilo se smazat otázku", true, err).then(self.reload, self.reload);
			}
			self.common.showLoader();
			self.common.async(asyncData, self.reload, cbFail, false);
		}
	}

	self.reload = function(useInitWaiter) {
	
		var cbFail = function(data) {
			self.common.hideLoader();
			self.common.hideInitLoader();
			self.clearMaterializedData();
			self.common.showError("Chyba", "Nepodařilo se nahrát data", true, data);
		};
		var cbOk = function(data) {
			self.common.hideLoader();
			self.common.hideInitLoader();
			self.clearMaterializedData();
			if (data && data.exams && data.questions && data.groups) {
				self.materialize(data);
			} else {
				cbFail("Nepodařilo se nahrát termíny kvůli špatné struktuře ze serveru");
			}
		};
		var asyncData = {
			"action" : "HANDLE_EXAMS",
			"exam_data" : "getData"
		};
		if(useInitWaiter === true) {
			self.common.showInitLoader("Nahrávám termíny", "orange");
		} else {
			self.common.showLoader();
		}
		self.common.async(asyncData, cbOk, cbFail, true);
	}

	self.init = function() {
		self.reload(true);
		document.body.appendChild(self.root);
		self.common.addLoginPanel();
		self.common.setLoginPanelVisible(true);
	}

	self.clearMaterializedData = function() {
		self.root.innerHTML = "";
		self.exams = [];
		self.groups = [];
		self.questions = [];
	}

	self.stopEditing = function() {
		for (var i = 0; i < self.exams.length; i++) {
			self.exams[i].setEditingEnabled(false);
		}
		for (var i = 0; i < self.groups.length; i++) {
			self.groups[i].setEditingEnabled(false);
		}
		for (var i = 0; i < self.questions.length; i++) {
			self.questions[i].setEditingEnabled(false);
		}
	}

	self.materializeExams = function(exData, templates) {
		var data = self.common.reconstructUI(templates.examManagerTableUI);
		var node = data[0];
		var ids = data[1];

		var tbody = ids.pnl_extable_tbody

		var visCB = function(vis) {self.root.style.display = vis ? "" : "none"};
		for (var i = 0; i < exData.exams.length; i++) {
			var exam = new ExamAdminExam(exData.exams[i], self.stopEditing, self.editExam, self.delExam, visCB, self.reload);
			self.exams.push(exam);
			tbody.appendChild(exam.node);
		}

		ids.ex_new.addEventListener("click", function() {
			var doc = prompt("Zadejte název nového termínu", "Vytvoření nového termínu");
			if (doc != null) {
				var cbOK = function(data) {
					self.common.hideLoader();
					self.reload();
				};
				var cbFail = function(data) {
					self.common.hideLoader();
					self.reload();
				}
				var asyncData = {
					"action" : "HANDLE_EXAMS",
					"exam_data" : "create_exam",
					"name" : doc
				};
				self.common.showLoader();
				self.common.async(asyncData, cbOK, cbFail, false);
			}
		});
		return node;
	}

	self.materializeGroups = function(exData, templates) {
		var data = self.common.reconstructUI(templates.examGroupUI)
		var node = data[0];
		var ids = data[1];

		var tbody = ids.pnl_body

		for (var i = 0; i < exData.groups.length; i++) {
			var group = new ExamAdminGroup(exData.groups[i], self.stopEditing, self.editGroup, self.delGroup);
			self.groups.push(group);
			tbody.appendChild(group.node);
		}

		ids.ex_new.addEventListener("click", function() {
			var doc = prompt("Zadejte název nové skupiny",
					"Vytvoření nové skupiny");
			if (doc != null) {
				var cbOK = function(data) {
					self.common.hideLoader();
					self.reload();
				};
				var cbFail = function(data) {
					self.common.hideLoader();
					self.reload();
				}
				var asyncData = {
					"action" : "HANDLE_EXAMS",
					"exam_data" : "create_group",
					"name" : doc
				};
				self.common.showLoader();
				self.common.async(asyncData, cbOK, cbFail, false);
			}
		});
		return node;
	}

	self.materializeQuestions = function(exData, templates) {
		var data = self.common.reconstructUI(templates.examQuestionUI)
		var node = data[0];
		var ids = data[1];

		var tbody = ids.pnl_body

		for (var i = 0; i < exData.questions.length; i++) {
			var question = new ExamAdminQuestion(exData.questions[i], exData.question_types, self.stopEditing, self.editQuestion, self.delQuestion);
			self.questions.push(question);
			tbody.appendChild(question.node);
		}

		ids.ex_new.addEventListener("click", function() {
			var doc = prompt("Zadejte název nové otázky",
					"Vytvoření nové otázky");
			if (doc != null) {
				var cbOK = function(data) {
					self.common.hideLoader();
					self.reload();
				};
				var cbFail = function(data) {
					self.common.hideLoader();
					self.reload();
				}
				var asyncData = {
					"action" : "HANDLE_EXAMS",
					"exam_data" : "create_question",
					"name" : doc
				};
				self.common.showLoader();
				self.common.async(asyncData, cbOK, cbFail, false);
			}
		});
		return node;
	}

	self.materialize = function(exData) {
		var contents = document.getElementById("contents");
		var templates = new ExamsAdminUITemplates();
		self.clearMaterializedData();

		// Sestavíme tabulku termínů
		self.root.appendChild(self.materializeExams(exData, templates));
		self.root.appendChild(self.materializeGroups(exData, templates));
		self.root.appendChild(self.materializeQuestions(exData, templates));

	}

	self.init();
	return this;
};

function adm_load() {
	window.getUI = new ExamsAdminUITemplates().getUI;
	new ExamAdminAdminer();
	if(window.pastAload) {
		window.pastAload();
	}
}

$INJECT(formats.js)$
$INJECT(exams/admin/templates.js)$
$INJECT(exams/admin/formats.js)$
$INJECT(WEB.ADMIN, admin.js)$
$INJECT(exams/index.js)$