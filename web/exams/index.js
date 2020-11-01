window.inject("common.js");

function ExamNoAccess() {
	self.common.showError("Chyba", "Nemáte dostatečná práva k přístupu k tomuto dokumentu", false);
}

window.inject_code_noperms("WEB.EXAMS.SEE", "window.exam_load = ExamNoAccess;");


var RadioRespose = function(data, permutation, readOnly) {
	var self = this;
	self.mapping = [];
	for(var i = 0; i < data.options.length; i++) {
		self.mapping.push[i];
	}
	if(permutation) {
		self.mapping = permutation.split("-").map(function(x){return x*1;});
	}
	
	self.revMapping = {};
	for(var i = 0; i < data.options.length; i++) {
		self.revMapping[self.mapping[i]] = i;
	}

	self.options = [];
	self.optionLabels = [];

	self.appendOption = function(optionID, optDescr, getLabelCB, setLabelCB, alignTop) {
		var row = document.createElement("tr");
		
		var leftCell = document.createElement("td");
		var rightCell = document.createElement("td");
		row.appendChild(leftCell);
		row.appendChild(rightCell);
		
		leftCell.style.width = "20px";
		
		leftCell.style.paddingBottom = "10px";
		rightCell.style.paddingBottom= "10px";
		
		if(alignTop) {
			leftCell.style.verticalAlign = "top";
		}
		
		var optNode = self.createOption(optionID);
		self.options.push(optNode);
		self.optionLabels.push([optDescr, getLabelCB, setLabelCB]);
		
		leftCell.appendChild(optNode);
		rightCell.appendChild(optDescr);
		
		self.node.appendChild(row);		
		optNode.style.verticalAlign = "top";
	}
	
	self.createOption = function(index) {
		var type = data.multiple ? "checkbox" : "radio";
		var node = document.createElement("input");
		node.addEventListener("change", self.optChange);
		node.type = type;
		node.value = index;
		return node;		
	}
	
	self.setValue = function(value) {
		for(var i = 0; i < value.length; i++) {
			var v = value[i];
			var label = v.value;
			var myID = v.ID !== undefined ? self.mapping[v.ID] : self.mapping.length;
			self.options[myID].checked = true
			self.optionLabels[myID][2](self.optionLabels[myID][0], label);
		}
	}
	
	self.getValue = function() {
		var val = [];
		for(var i = 0; i < self.options.length; i++) {
			var realI = self.revMapping[i];
			var value = self.options[i].checked ? true : false;
			var label = self.optionLabels[i][1](self.optionLabels[i][0]);
			if(value) {
				if(realI !== undefined) {
					val.push({"ID": realI, "value": label});
				} else {
					val.push({"value": label});
				}
			}
		}
		return val;
	}
	
	self.optChange = function(ev) {
		var el = ev.target;
		if(readOnly) {
			return el.checked = !el.checked;
		} else {
			if(!data.multiple) {
				for(var i = 0; i < self.options.length; i++) {
					if(self.options[i] != el) {
						self.options[i].checked = false;
					}	
				}
			}
		}
	}
	
	self.materialize = function() {
		self.node = document.createElement("table");
		self.node.style.width = "100%";
		self.node.style.border = "0px";
		var outFormat = new CommonFormats();
		
		for(var i = 0; i < data.options.length; i++) {
			var opt = outFormat.format(data.options[self.mapping[i]]);
			var optDescr = document.createElement("span");
			optDescr.innerHTML = opt;
			var getLabelCB = function(labelObj) {return labelObj.innerHTML;};
			var setLabelCB = function(labelObj, label) {labelObj.innerHTML = outFormat.format(label);};
			self.appendOption(self.mapping[i], optDescr, getLabelCB, setLabelCB);
		}
	}
	
	self.init = function() {
		self.materialize();
	}
	
	self.init();
	return this;
};

var RadioCustomRespose = function(data, initialContents, readOnly) {
	var self = this;
	var internal = new RadioRespose(data, initialContents, readOnly);
	
	self.materialize = function() {
		self.node = internal.node;
		self.customEditor = document.createElement("textarea");
		var getLabelCB = function(editor) {return editor.value;};
		var setLabelCB = function(editor, value) {editor.value = value;};
		self.customEditor.readOnly = readOnly;
		internal.appendOption(data.options.length, self.customEditor, getLabelCB, setLabelCB, true);
	}
	
	self.setValue = function(value) {
		return internal.setValue(value);
	}
	
	self.getValue = function() {
		var radioValue = internal.getValue();
		return radioValue;
	}
	
	self.init = function() {
		self.materialize();
	}
	
	self.init();
	return this;
};

var InputTextResponse = function(data, initialContents, readOnly) {
	var self = this;
	
	self.materialize = function() {
		self.node = document.createElement("input");
		self.node.type = "text";
		self.node.value = initialContents;
		self.node.readOnly = readOnly;
	}
	
	self.setValue = function(value) {
		return self.node.value = value;
	}
	
	self.getValue = function() {
		return self.node.value;
	}
	
	self.init = function(){
		self.materialize();
	}
	
	self.init();
	return this
}

var TextAreaResponse = function(data, initialContents, readOnly) {
	var self = this;
	
	self.materialize = function() {
		self.node = document.createElement("textarea");
		self.node.innerHTML = initialContents;
		self.node.readOnly = readOnly;
	}
	
	self.setValue = function(value) {
		return self.node.value = value;
	}
	
	self.getValue = function() {
		return self.node.value;
	}
	
	self.init = function(){
		self.materialize();
	}
	
	self.init();
	return this
};

var ExamQuestion = function(data, index, readOnly, fromAdmin) {
	var self = this;
	self.ID = data.ID;
	
	
	self.common = new Common();
	self.templates = new UITemplates();
	
	var responseTemplates = [RadioRespose, RadioCustomRespose, InputTextResponse, TextAreaResponse];
	
	self.pointsFmt = function(bodu) {
		return bodu +" bod" + (bodu == 1 ? "" : bodu >= 2 && bodu <= 4 ? "y" : "ů");
	}
	
	self.setValue = function(values) {
		for(var i = 0; i < values.length; i++) {
			if(values[i].ID == self.ID) {
				self.responder.setValue(values[i].Value);
				return;
			}
		}
	}
	
	self.getValue = function() {
		return {"ID": self.ID, "Permutations": data.Permutations, "Value": self.responder.getValue()};
	}
	
	self.collectEval = function(cb) {
		return {"ID": self.ID, "eval": self.evalPoints.value+"", "comment": self.evalComment.value};
	}
	
	self.materialize = function() {
		var outFormat = new CommonFormats(); 
		var d = self.common.reconstructUI(self.templates.QuestionUI);
		self.node = d[0];
		
		var ids = d[1];
		
		ids.question_id.innerHTML = outFormat.format("Otázka " + (index + 1));
		ids.question_contents.innerHTML = outFormat.format(data.Config.description);
		
		if(data.Config.type >= 0 && data.Config.type < responseTemplates.length) {
			self.responder = new responseTemplates[data.Config.type](data.Config, data.Permutations, readOnly);
			
			ids.pnl_resp_cont.appendChild(self.responder.node);
		}
		
		self.evalPoints = ids.pln_eval_pints_edit;
		self.evalComment = ids.pln_comment_edit;
		
		ids.qb_points.innerHTML = outFormat.format(self.pointsFmt(data.Evaluation));
		ids.pnl_eval_row.style.display = "none";
		ids.pnl_eval_commentrow.style.display = "none";
		ids.pln_eval_pints_view.style.display = "none";
		ids.pln_eval_pints_edit.style.display = "none";
		ids.pln_comment_view.style.display = "none";
		ids.pln_comment_edit.style.display = "none";
		
		if (data.EvaluationData && data.EvaluationData.points) {
			ids.pnl_eval_row.style.display = "";
			ids.pln_eval_pints_view.innerHTML = data.EvaluationData.points;
			ids.pln_eval_pints_edit.value = data.EvaluationData.points;
			var el = fromAdmin ? ids.pln_eval_pints_edit : ids.pln_eval_pints_view;
			el.style.display = "";
			if (data.EvaluationData.comment != "") {
				ids.pnl_eval_commentrow.style.display = "";
				ids.pln_comment_view.innerHTML = data.EvaluationData.comment;
				ids.pln_comment_edit.value= data.EvaluationData.comment;	
				el = fromAdmin ? ids.pln_comment_edit : ids.pln_comment_view;
				el.style.display = "";
			}
		}
		if(fromAdmin) {
			ids.pln_eval_pints_edit.style.display = "";
			ids.pln_eval_pints_view.style.display = "none";
			ids.pln_comment_edit.style.display = "";
			ids.pln_comment_view.style.display = "none";
			ids.pnl_eval_row.style.display = "";
			ids.pnl_eval_commentrow.style.display = "";
		}
	}
	
	
	self.init = function() {
		self.materialize();	
	}
	
	self.init();
	return this;
}


var ExamMainHeader = function(data, startCB, turnCB, saveCB, fromAdmin) {
	var self = this;
	
	if(fromAdmin) {
		data.ReadOnly = true;
	}
	
	self.data = data;
	self.templates = new UITemplates();
	self.common = new Common();
	
	self.node = null;
	
	self.examBeginTime = undefined;
	self.examLength = undefined
	
	self.tick = function() {
		var now  = self.common.getRemoteNow();
		
		if(self.pnlDate && self.pnlTime) {
			self.pnlDate.innerHTML = self.common.convertDate(now);
			self.pnlTime.innerHTML = self.common.convertOnlyTime(now); 
		}
		if(self.pnlCountdown && self.examBeginTime !== undefined) {
			if(now - self.examBeginTime > self.examLength) {
				self.btnTurn.style.display = "none";
			} else {
				self.pnlCountdown.innerHTML = self.common.convertTime(self.examLength - (now - self.examBeginTime));
			}
		}
		setTimeout(self.tick, 750);
	}
	
	self.publishEval = function() {
		return !!self.publishEvalPnl.checked
	}
	
	self.pointsFmt = function(bodu) {
		return bodu +" bod" + (bodu == 1 ? "" : bodu >= 2 && bodu <= 4 ? "y" : "ů");
	}
	
	self.materialize = function() {
		var outFormat = new CommonFormats();
		var d = self.common.reconstructUI(self.templates.HeaderUI);
		self.node = d[0];
		var ids = d[1];
		
		ids.pnl_ex_name.innerHTML = outFormat.format(data.ExamName);
		if(fromAdmin) {
			ids.pnl_name.innerHTML = fromAdmin.name;
		} else {
			ids.pnl_name.innerHTML = self.common.identity.name;
		}
		ids.pnl_total_questions.innerHTML = data.TotalQuestions;
		ids.pnl_time_left.innerHTML = self.common.convertTime(data.ExamTime)
		
		self.examLength = data.ExamTime
		
		self.btnStart = ids.btn_start;
		self.btnTurn = ids.btn_submit;
		
		self.pnlDate = ids.pnl_date_now;
		self.pnlTime = ids.pnl_time_now;
		self.pnlCountdown = ids.pnl_time_left;
		self.pnlCountdownLbl = ids.pnl_time_left_lbl;
		
		self.btnStart.addEventListener("click", function(){startCB(data.ExamID)});
		self.btnTurn.addEventListener("click", function(){turnCB(data.ExamID)});
		
		ids.eval_headerrow.style.display = "none";
		
		if(data.EvaluatedAt) {
			ids.eval_headerrow_evaltime.innerHTML = self.common.convertDateTime(data.EvaluatedAt);
		}
		if(data.EvaluationAvailable) {
			ids.eval_headerrow.style.display = "";
			var points = 0;
			for(var i = 0; i < data.Questions.length; i++) {
				var q = data.Questions[i];
				if(q.EvaluationData && q.EvaluationData.points) {
					var p = q.EvaluationData.points*1;
					points += p;
				}
			}
			points = (Math.floor(points*100))/100;
			ids.eval_headerrow_points.innerHTML = points + " " + (points == 1 ? "bod" : points >= 2 && points <= 4 ? "body" : "bodů");
		}
		
		self.publishEvalPnl = ids.eval_headerrow_publish_eval;
		
		if(fromAdmin) {
			ids.btn_back.addEventListener("click", fromAdmin.close);
			ids.btn_save.addEventListener("click", function() {saveCB(fromAdmin);});
			ids.eval_headerrow2.style.display = "";
			if(data.EvaluationAvailable) {
				self.publishEvalPnl.checked = true;
			}
		} else {
			ids.eval_headerrow2.style.display = "none";
			ids.btn_back.style.display = "none";
			ids.btn_save.style.display = "none";
		}
		
		if(data.ReadOnly) {
			self.btnStart.style.display = "none";
			self.btnTurn.style.display = "none";
			self.pnlCountdown.innerHTML =self.common.convertDate(data.AnswerTime) + " "+  self.common.convertOnlyTime(data.AnswerTime);
			self.pnlCountdownLbl.innerHTML = "Čas vyplnění";
			if(!fromAdmin) {
				ids.row_act.style.display = "none"
			}
		} else if (data.StartedAt !== undefined) {
			self.btnStart.style.display = "none";
			self.examBeginTime = data.StartedAt;
		} else {
			self.btnTurn.style.display = "none";
		}
		
		ids.pnl_total_points.innerHTML = self.pointsFmt(data.ExamTotalPoints);
		
		self.tick();
	}

		
	self.init = function() {
		self.materialize();
	}
	
	this.init();
	return self;
}

var ExamExamer = function(initLoad) {
	var self = this;
	self.common = new Common();
	self.root = document.createElement("div");
	self.root.classList.add("ex_root")
	
	self.questions = [];
	
	self.startExam = function(examID) {
		self.common.showLoader();
		var cbOK = function(data) {
			self.common.hideLoader();
			if(data.Available === true) {
				self.materialize(data);
			} else {
				self.common.showError("Chyba načítání dat", "Nepodařilo se spuštění termínu, protože není spustitelný", true).then(self.common.hideLoader, self.common.hideLoader);
			}
		}
		var cbFail = function(err) {
			self.common.showError("Chyba načítání dat", "Nepodařilo se spuštění termínu", true, err).then(self.common.hideLoader, self.common.hideLoader);
		}
		var asyncData = {
			"action" : "HANDLE_EXAMS",
			"exam_data" : "begin_exam",
			"ID": examID
		};
		self.common.async(asyncData, cbOK, cbFail, true);	
	}
	
	self.turnExam = function(examID) {
		var resp = [];
		for(var i = 0; i < self.questions.length; i++) {
			var q = self.questions[i];
			resp.push(q.getValue());
		}	
		self.common.showLoader();
		var cbOK = function(data) {
			self.common.hideLoader();
			if(data.Available === true) {
				self.materialize(data);
			} else {
				self.common.showError("Chyba odevzdání", "Nepodařilo se odevzdat odpověď k termínu", true, data).then(self.common.hideLoader, self.common.hideLoader);
			}
		}
		var cbFail = function(err) {
			self.common.hideLoader();
			self.common.showError("Chyba odevzdání", "Nepodařilo se odevzdat odpověď k termínu", true, err).then(self.common.hideLoader, self.common.hideLoader);
		}
		var asyncData = {
			"action" : "HANDLE_EXAMS",
			"exam_data" : "finish_exam",
			"ID": examID,
			"Data": resp
		};
		self.common.async(asyncData, cbOK, cbFail, true);	
	}
	
	self.saveExam = function(fromAdmin) {
		var data = [];
		for(var i = 0; i < self.questions.length; i++) {
			data.push(self.questions[i].collectEval());
		}
		var save = self.mainHeader.publishEval();
		fromAdmin.save({"evals": data, "save": save});
	}
	
	self.materialize = function(data, fromAdmin) {
		self.root.innerHTML = "";
		self.mainHeader = new ExamMainHeader(data, self.startExam, self.turnExam, self.saveExam, fromAdmin);
		self.root.appendChild(self.mainHeader.node);
			
		if(data.Questions) {
			self.questions = [];
			for(var i = 0; i < data.Questions.length; i++) {
				var q = new ExamQuestion(data.Questions[i], i, data.ReadOnly, fromAdmin);
				self.questions.push(q);
				var br = document.createElement("br");
				self.root.appendChild(br);
				self.root.appendChild(q.node);
			}
			
			if(data.Answers) {
				for(var i = 0; i < self.questions.length; i++) {
					self.questions[i].setValue(data.Answers);
				}
			}
		}
	}

	self.loadExam = function(optGenID, fromAdmin) {
		var cbOK = function(data) {
			self.common.hideInitLoader();
			self.common.hideLoader();
			if(data.Available === true) {
				self.materialize(data, fromAdmin);
			} else {
				self.common.hideInitLoader();
				self.common.hideLoader();
				self.common.showError("Chyba zobrazení", "Není k dispozici žádný termín k zobrazení", false)
			}
		}
		var cbFail = function(err) {
			self.common.hideLoader();
			self.common.showError("Chyba načítání termínu", "Nepodařilo se načíst termín", false, err);
		}
		var asyncData = {
			"action" : "HANDLE_EXAMS",
			"exam_data" : "get_exam"
		};
		if(optGenID) {
			asyncData.exam_data = "get_result_by_id";
			asyncData.ID = optGenID;
		}
		self.common.async(asyncData, cbOK, cbFail, true);
	}
	
	self.init = function() {
		document.body.appendChild(self.root);
		if(initLoad!==false) {
			self.common.setLoginPanelVisible(false);
			self.common.showInitLoader("Nahrávám termín...", "grey");
			self.loadExam();
		}
	}
	
	self.init();
	return this;

}

function exam_load() {
	var ex = new ExamExamer(true);
	if(window.pastAload) {
		window.pastAload();
	}
}

window.inject("exams/templates.js");
window.inject("formats.js");
window.inject("WEB.ADMIN", "admin.js");