if(!window.Tester) {
	window.Tester = {}
}

window.Tester.Templates = function() {
	var self = this;
	
	var btnStyle = "height: 30px; padding-left: 20px; padding-right: 20px;";
	
	self.UI = {
		"type": "table",
		"class": "tester_tbl",
		"id": "marginPnl",
		"style": "border-collapse: collapse; border-spacing: 0px; border: 1px solid black;width:80%;min-width: 840px;margin-bottom: 80px;font-family: Tahoma;left: 10%;position: relative;",
		"contents": [
			{
				"type": "thead",
				"contents": [
					{
						"type": "tr",
						"id": "nwBorder",
						"style": "background-color: #eeeeee;line-height: 50px;font-size: 20pt;font-weight: normal; border-bottom: 1px solid black",
						"contents": [
							{
								"type": "td",
								"innerHTML": "Header",
								"id": "txtBrief",
								"style": "width: 100%;padding-left:20px;"
							},
							{
								"type": "td",
								"style": "padding-right: 10px;min-width: 400px;text-align: right;",
								"contents": [
									{
										"type": "span",
										"id": "lblUnread",
										"style": "background: #fcba03; display: inline; font-size: 11pt;margin-right: 20px;position: relative; top: -2px; padding: 6px; border-radius: 15px; border: 2px solid black",
										"contents": [
											{
												"type": "span",
												"innerHTML": "Nové komentáře ("
											},
											{
												"type": "span",
												"id": "lblUnreadLbl"
											},
											{
												"type": "span",
												"innerHTML": ")&nbsp;&nbsp;&nbsp;"
											},
											{
												"type": "span",
												"id": "lblUnreadBtn",
												"style": "cursor: pointer",
												"innerHTML": "[X]"
											}
										]
									},							
									{
										"type": "button",
										"innerHTML": "Skrýt",
										"id": "btnHide",
										"style": (btnStyle + "; position: relative; top: -5px; text-align: center; display: inline-block; width: 80px;padding: 0px;")
									}
								]		
							}
						]
					}
				]
			},
			{
				"type": "tbody",
				"contents": [
					{
						"type": "tr",
						"id": "pnlMain",
						"style": "border-bottom: 1px solid black",
						"contents": [
							{
								"type": "td",
								"colSpan": 2,
								"style": "margin: 0px; padding: 0px;",
								"contents": [
									{
										"type": "table",
										"style": "border-collapse: collapse; border: 0px; width: 100%; margin: 0px; padding: 0px;",
										"contents": [
											{
												"type": "tbody",
												"contents": [
													{
														"type": "tr",
														"contents": [
															{
																"type": "td",
																"style": "vertical-align: top",
																"contents": [
																	{
																		"type": "textarea",
																		"id": "txtArea",
																		"style": "width: calc(100% - 5px); min-height: 230px; height: 100%; padding-left: 5px; padding-right: 0px; outline: none; border-left: none; border-right: none; border-top: none; border-bottom: 1px solid black; background: #fafafa; font-family: courier; resize: vertical;",
																	}
																]
															},
															{
																"type": "td",
																"rowSpan": 2,
																"style": "vertical-align: top; width: 33%; border-left: 1px solid black; border-right: 1px solid black;margin: 0px; padding: 0px;",
																"contents":[
																	{
																		"type": "div",
																		"style": "height: 40px; background-color: #daffff;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;border-bottom: 1px solid black",
																		"innerHTML": "Zadání"
																	},
																	{
																		"type": "div",
																		"style": "min-height: 230px;height: 100%;padding: 5px;font-family: verdana;font-size:10pt;",
																		"id": "txtDescr",
																		"innerHTML": "Text pro popis zadání"
																	}
																]	
															},
															{
																"type": "td",
																"rowSpan": 2,
																"style": "width: 20%;min-width: 320px; vertical-align: top;",
																"colSpan": 3,
																"contents":[
																	{
																		"type": "div",
																		"style": "height: 40px; background-color: #ffddcc;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;border-bottom: 1px solid black",
																		"innerHTML": "Řešení"
																	},
																	{
																		"type": "div",
																		"style": "min-height: 230px;height: 100%;padding: 5px;font-family: times;",
																		"id": "txtSolution"
																	}
																]
															}
														]
													},
													{
														"type": "tr",
														"contents": [
															{
																"type": "td",
																"style": "padding-right: 10px;width: 33%;",
																"contents": [
																	{
																		"type": "button",
																		"innerHTML": "Otestovat řešení",
																		"id": "runtests",
																		"style": btnStyle + "; width: 80px;position: relative;margin-left: 5px;margin-bottom: 6px; width: 150px; height: 28px;"
																	},
																	{
																		"type": "button",
																		"innerHTML": "Historie řešení",
																		"id": "showhist",
																		"style": btnStyle + "; width: 80px;position: relative;margin-left: 5px;margin-bottom: 6px; width: 150px; height: 28px;"
																	},
																	{
																		"type": "button",
																		"id": "btnHistFiltr",
																		"style": btnStyle + "; width: 80px;position: relative;margin-left: 5px;margin-bottom: 6px; width: 150px; height: 28px;",
																		"innerHTML": "Filtr historie"
																	},
																	{
																		"type": "span",
																		"id": "timeoutLbl"
																	}
																]	
															}	
														]
													}
												]
											}
										]
									}
								]
							}
						]
					},
					{
						"type": "tr",
						"style": "",
						"contents": [
							{	
								"type": "td",
								"id": "feedbackPnl",
								"colSpan": 2,
								"style": "padding: 0px; margin: 0px; display: none"
							}	
						]
					}
				]
			}
		]
	}
	
	self.filterRowUI = {
		"type": "div",
		"style": "border-bottom: 1px solid black;margin: 0px; padding: 0px;",
		"contents": [
			{
				"type": "div",
				"style": "display: inline-block; width: 80px; text-align: center; border-right: 1px solid black;padding-top: 5px;padding-bottom: 5px;",
				"contents": [
					{
						"base": "input",
						"type": "checkbox",
						"id": "check",
					}
				]
			},
			{
				"type": "div",
				"style": "display: inline; padding-top: 5px;padding-bottom: 5px;padding-left: 10px;",
				"id": "text"
			}
		]
	}
		
	self.filterUI = {
		"type": "div",
		"style": "position: fixed; left: 0px; right: 0px; top: 0px; bottom: 0px; background-color: #000000aa; font-family: Verdana",
		"contents": [
			{
				"type": "div",
				"style": "display: block; width: 80%; min-height: 400px; height: 50%; min-width: 600px;border:2px solid black; background: white;position: relative;left:10%;top:25%;",
				"contents": [
					{
						"type": "div",
						"style": "width: 100%; height: 40px; background: #aaaadd; border-bottom: 2px solid black;text-align: center;line-height: 40px; font-size: 20pt;",
						"contents": [
							{
								"type": "span",
								"innerHTML": "Výběr dat k zobrazení"
							},
							{
								"type": "button",
								"id": "btnClose",
								"style": "float: right; margin-right: 6px; padding-left: 20px; padding-right: 20px; padding-top: 4px; padding-bottom:4px; margin-top:6px;",
								"innerHTML": "Zavřít"
							},
							{
								"type": "button",
								"id": "btnSet",
								"style": "float: right; margin-right: 6px; padding-left: 20px; padding-right: 20px; padding-top: 4px; padding-bottom:4px; margin-top:6px;",
								"innerHTML": "Nastavit výběr"
							},
							{
								"type": "checkbox",
								"base": "input",
								"id": "checkRemember",
								"style": "float: right; margin-right: 6px; padding-right: 20px; padding-top: 4px; padding-bottom:4px; margin-top:15px;",
							},
							{
								"type": "div",
								"style": "font-size: 12pt; display: inline-block; float: right; margin-right: 6px;line-height:40px;",
								"innerHTML": "Zapamatovat"
							}
						]		
					},
					{
						"type": "div",
						"style": "display: block; height: calc(100% - 40px); overflow-y: auto",
						"contents": [
							{
								"type": "table",
								"style": "border-collapse:collapse;width: 100%; height: 100%;overflow-y: auto",
								"contents": [
									{
										"type": "thead",
										"contents": [
											{
												"type": "tr",
												"style": "border-bottom: 1px solid black;background-color:#dedede;",
												"contents": [
													{
														"type": "th",
														"colSpan": 2,
														"style": "font-size: 12pt; text-align: center; padding-top: 5px; padding-bottom: 5px; border-right: 1px solid black; width: 50%;border-right: 3px solid black;",
														"innerHTML": "Po skupinách"
													},
													{
														"type": "th",
														"colSpan": 2,
														"style": "font-size: 12pt; text-align: center; padding-top: 5px; padding-bottom: 5px; width: 50%",
														"innerHTML": "Konkrétní loginy"
													}
												]
											},
											{
												"type": "tr",
												"style": "border-bottom: 1px solid black;background-color:#dedede;",
												"contents": [
													{
														"type": "th",
														"style": "font-size: 12pt; padding-top: 5px; padding-bottom: 5px; text-align: right; padding-right: 10px; border-right: 1px solid black;",
														"innerHTML": "Výběr"
													},
													{
														"type": "th",
														"id": "selection_groups",
														"style": "font-size: 12pt; text-align: left; padding-top: 5px; padding-bottom: 5px; border-right: 3px solid black;width: calc(100% - 50px);background: #ffffff; padding-left: 10px",
														"innerHTML": "selected groups"
													},
													{
														"type": "th",
														"style": "font-size: 12pt; padding-top: 5px; padding-bottom: 5px; text-align: right; padding-right: 10px; border-right: 1px solid black;",
														"innerHTML": "Výběr"
													},
													{
														"type": "th",
														"id": "selection_logins",
														"style": "font-size: 12pt; text-align: left; padding-top: 5px; padding-bottom: 5px; width: calc(100% - 50px);background: #ffffff; padding-left: 10px",
														"innerHTML": "selected logins"
													}
												]
											},
											{
												"type": "tr",
												"style": "border-bottom: 1px solid black;background-color:#dedede;border-bottom: 2px solid black;",
												"contents": [
													{
														"type": "th",
														"style": "font-size: 12pt; padding-top: 5px; padding-bottom: 5px; text-align: right; padding-right: 10px; border-right: 1px solid black;",
														"innerHTML": "Filtr"
													},
													{
														"type": "th",
														"style": "font-size: 12pt; text-align: left; padding-top: 5px; padding-bottom: 5px; border-right: 3px solid black;width: calc(100% - 50px);background: #ffffff; padding-left: 10px",
														"contents": [
															{
																"type": "input",
																"id": "filter_groups",
																"style": "outline: none; width: calc(100% - 20px);padding-top: 2px; padding-bottom: 2px; border: 0px"
															}
														]	
													},
													{
														"type": "th",
														"style": "font-size: 12pt; padding-top: 5px; padding-bottom: 5px; text-align: right; padding-right: 10px; border-right: 1px solid black;",
														"innerHTML": "Filtr"
													},
													{
														"type": "th",
														"style": "font-size: 12pt; text-align: left; padding-top: 5px; padding-bottom: 5px; width: calc(100% - 50px);background: #ffffff; padding-left: 10px",
														"contents": [
															{
																"type": "input",
																"id": "filter_logins",
																"style": "outline: none; width: calc(100% - 20px);padding-top: 2px; padding-bottom: 2px; border: 0px"
															}
														]	
													}
												]
											}
										]
									},
									{
										"type": "tbody",
										"contents": [
											{
												"type": "tr",
												"style": "height: 100%;margin: 0px; padding: 0px;",
												"contents": [
													{
														"type": "td",
														"colSpan": 2,
														"style": "height: 100%;margin: 0px; padding: 0px;vertical-align:top;border-bottom: 3px solid black;",
														"contents": [
															{
																"type":"div",
																"id": "contents_groups"																		
															}
														]
													},
													{
														"type": "td",
														"colSpan": 2,
														"style": "height: 100%;margin: 0px; padding: 0px;vertical-align:top;border-bottom: 3px solid black;",
														"contents": [
															{
																"type":"div",
																"id": "contents_users"																		
															}
														]
													}
												]
											}
										]								
									}
								]
							}
						]
					}
				]
			}
		]
	};
	
	self.protocolRowUI = {
		"type": "tr",
		"style": "border-bottom: 1px solid black",
		"contents": [
			{
				"type": "td",
				"id": "cnt",
				"style": "border-right: 1px solid black; text-align: right;padding-right: 10px;padding-top: 5px; padding-bottom: 5px;"
			},
			{
				"type": "td",
				"id": "type",
				"style": "border-right: 1px solid black; text-align: center;padding-top: 5px; padding-bottom: 5px;"
			},
			{
				"type": "td",
				"id": "contents",
				"style": "border-right: 1px solid black; text-align: left; font-family: courier;padding-right: 10px;padding-left:10px;padding-top: 5px; padding-bottom: 5px;"
			}
		]
	};
	
	self.protocolUI = {
		"type": "div",
		"style": "position: fixed; left: 0px; right: 0px; top: 0px; bottom: 0px; background-color: #000000aa; font-family: Verdana",
		"contents": [
			{
				"type": "div",
				"style": "display: block; width: 50%; min-height: 400px; height: 50%; min-width: 600px;border:2px solid black; background: white;position: relative;left:25%;top:25%;",
				"contents": [
					{
						"type": "div",
						"style": "width: 100%; height: 40px; background: #aaaadd; border-bottom: 2px solid black;text-align: center;line-height: 40px; font-size: 20pt;",
						"contents": [
							{
								"type": "span",
								"innerHTML": "Protokol"
							},
							{
								"type": "button",
								"id": "btnClose",
								"style": "float: right; margin-right: 6px; padding-left: 20px; padding-right: 20px; padding-top: 4px; padding-bottom:4px; margin-top:6px;",
								"innerHTML": "Zavřít"
							}
						]		
					},
					{
						"type": "div",
						"style": "display: block; height: calc(100% - 40px); overflow-y: auto",
						"contents": [
							{
								"type": "table",
								"style": "border-collapse:collapse;width: 100%; height: 100%;overflow: auto",
								"contents": [
									{
										"type": "thead",
										"contents": [
											{
												"type": "tr",
												"style": "border-bottom: 1px solid black;background-color:#dedede;",
												"contents": [
													{
														"type": "th",
														"style": "font-size: 12pt; text-align: center; padding-top: 5px; padding-bottom: 5px; border-right: 1px solid black; width: 80px;",
														"innerHTML": "#"
													},
													{
														"type": "th",
														"style": "font-size: 12pt; text-align: center; padding-top: 5px; padding-bottom: 5px; border-right: 1px solid black; width: 150px;",
														"innerHTML": "Typ"
													},
													{
														"type": "th",
														"style": "font-size: 12pt; text-align: center; padding-top: 5px; padding-bottom: 5px",
														"innerHTML": "Obsah"
													}
												]
											}
										]
									},
									{
										"type": "tbody",
										"id": "contents"
									}
								]
							}
						]
					}
					
			
			
				]
				
			}
			
		]
	}
	
	self.feedbackListRowUI = {
		"type": "tr",
		"style": "border-top: 1px solid black",
		"contents": [
			{
				"type": "td",
				"id": "loginCell",
				"style": "padding-right: 10px; padding-top: 3px; padding-bottom: 3px;border-right: 1px solid black; text-align: right"
			 },
		     {
				"type": "td",
				"id": "turndate",
				"style": "padding-right: 10px; padding-top: 3px; padding-bottom: 3px;border-right: 1px solid black; text-align: right",
				"innerHTML": "01.01.1970 22:00"
			 },
			 {
				"type": "td",
				"id": "results",
				"style": "padding-right: 10px; padding-top: 3px; padding-bottom: 3px;border-right: 1px solid black; text-align: right",
				"contents": [
					{
						"type": "span",
						"id": "results"
					},
					{
						"type": "button",
						"id": "btnProtocol",
						"style": "float: right; margin-right: 1px;margin-left: 10px; height: 30px; padding-left:20px; padding-right: 20px;",
						"innerHTML": "Zobrazit protokol"
					}
				]
			 },
		 	 {
				"type": "td",
				"id": "comments",
				"style": "padding-right: 10px; padding-top: 3px; padding-bottom: 3px;border-right: 1px solid black; text-align: right",
				"innerHTML": "0"
			 },
		 	 {
				"type": "td",
				"style": "padding-right: 10px; padding-top: 3px; padding-bottom: 3px;border-right: 1px solid black; text-align: left",
				"contents": [
					{
						"type": "span",
						"style": "margin-left: 10px",
						"id": "lastcommentlogin"
					},
					{
						"type": "span",
						"style": "float: right;margin-right: 5px;",
						"id": "lastcomment"
					}
				]
			 },
		 	 {
				"type": "td",
				"id": "btnCommentCellForBorder",
				"style": "padding-right: 5px; padding-top: 5px; padding-bottom: 5px;border-right: 1px solid black; text-align: right; width: 180px;",
				"contents": [
					{
						"type": "button",
						"id": "btnComment",
						"style": btnStyle,
						"innerHTML": "Zobrazit komentáře"
					}
				]
			 }
		]
	}
	
	self.feedbackListUI = {
		"type": "table",
		"style": "border-collapse: collapse; border: 0px; width: 100%; margin: 0px; padding: 0px;",
		"contents": [
			{
				"type": "thead",
				"contents": [
					{
						"type":"tr",
						"style": "border-bottom: 1px solid black",
						"contents": [
							{
								"type": "td",
								"id": "loginHeader",
								"style": "border-right: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 150px;",
								"innerHTML": "Login",
							},
							{
								"type": "td",
								"style": "border-right: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 15%;",
								"innerHTML": "Datum odevzdání",
							},
							{
								"type": "td",
								"style": "border-right: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 30%;",
								"innerHTML": "Výsledek testů",
							},
							{
								"type": "td",
								"style": "border-right: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 15%;",
								"innerHTML": "Počet komentářů",
							},
							{
								"type": "td",
								"style": "border-right: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 20%;",
								"innerHTML": "Poslední komentář",
							},
							{
								"type": "td",
								"style": "height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 150px;",
								"innerHTML": "Akce"
							}
						]
					}
				]
			},
			{
				"type": "tbody",
				"id": "feedback_contents"
			}
		]
	}
	
	self.commentUI = {
		"type": "table",
		"style": "border-collapse: collapse; border-top: 0px; width: 100%; margin: 0px; padding: 0px;",
		"contents": [
			{
				"type": "tbody",
				"contents": [
					{
						"type":"tr",
						"contents": [
							{
								"type": "td",
								"style": "border-bottom: 1px solid black; border-right: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 50%;",
								"innerHTML": "Odevzdaný kód",
							},
							{
								"type": "td",
								"style": "border-bottom: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: calc(50% - 178px);",
								"innerHTML": "Komentáře"
							},
							{
								"type": "td",
								"style": "border-left: 1px solid black; border-bottom: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 178px;min-width:178px",
								"innerHTML": "Akce"
							}
						]
					},
					{
						"type": "tr",
						"contents": [
							{
								"type": "td",
								"id": "leftCell",
								"style": "height: 100%;vertical-align: top;padding-left: 5px;",
								"contents": [
									{
										"type": "pre",
										"style": "min-height: 150px; padding: 5x; font-family: Courier; font-size: 12pt; border-right: 1px solid black;outline: none; width: calc(100% - 10px); height: calc(100% - 10px); resize: vertical;border: 0px",
										"id": "codecontents"
									}
								]
							},
							{
								"type":"td",
								"id": "midcell",
								"style": "vertical-align: top; height: 100%;margin: 0px; padding: 0px;border-left: 1px solid black;",
								"contents": [
									{
										"type": "table",
										"id": "comTable",
										"style": "border-collapse: collapse; border: 0px; width: 100%; margin: 0px; padding: 0px;",
										"contents": [
											{
												"type": "tbody",
												"id": "commentcontents"
											}
										]
									},
									{
										"type": "table",
										"id": "newComTable",
										"style": "border-collapse: collapse; border: 0px; width: 100%; height: 100%; margin: 0px; padding: 0px;",
										"contents": [
											{
												"type": "thead",
												"contents": [
													{
														"type": "tr",
														"contents": [
															{
																"type": "td",
																"style": "border-bottom: 1px solid black; border-right: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 200px;",
																"innerHTML": "Označení"
															},
															{
																"type": "td",
																"style": "border-bottom: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: calc(100% - 200px); min-width: 300px;",
																"innerHTML": "Komentář"
															}
														]
													}
												]
											},
											{
												"type": "tbody",
												"style": "border-collapse: collapse; border: 0px; width: 100%; height: 100%; margin: 0px; padding: 0px;",
												"contents": [
													{
														"type": "tr",
														"contents": [
															{
																"type": "td",
																"style": "border-right: 1px solid black; padding: 10px; vertical-align: top;",
																"id": "commentSel"
															},
															{
																"type": "td",
																"contents": [
																	{
																		"type": "textarea",
																		"id": "commentArea",
																		"style": "min-height: 120px; position: relative; top: 5px; font-family: Verdana; font-size: 14pt; resize: vertical; height: calc(100% - 10px); width: calc(100% - 10px); outline: none; border: 0px;"
																	}
																]																	
															}
														]
													}
												]
											}
										]
									}
								]
							},
							{
								"type": "td",
								"style": "border-left: 1px solid black; vertical-align: top; text-align: right;padding-right: 5px; padding-top: 5px; padding-bottom: 5px;",
								"contents": [
									{
										"type": "button",
										"id": "btnComment",
										"style": btnStyle,
										"innerHTML": "Přidat komentář"
									},
									{
										"type": "button",
										"id": "btnSave",
										"style": btnStyle + "; margin-top: 5px;display: none",
										"innerHTML": "Uložit komentář"
									},
									{
										"type": "button",
										"id": "btnAddSel",
										"style": btnStyle + "; margin-top: 5px;",
										"innerHTML": "Přidat označení"
									},
									{
										"type": "button",
										"id": "btnCancelEdit",
										"style": btnStyle + "; margin-top: 5px;display: none",
										"innerHTML": "Zrušit editaci"
									}
								]
							}
						]
					}
				]
			}
		]
	};
	
	self.FeedbackLoadedRowUI =  {
		"type": "tr",
		"style": "border-bottom: 1px solid black",
		"contents": [
			{
				"type": "td",
				"colSpan": 2,
				"contents": [
					{
						"type": "div",
						"style": "width: 100%; display: block;background:#aaffdd;line-height: 30px;border-bottom: 1px solid black;",
						"contents": [
							{
								"type": "span",
								"style": "padding-left:10px;",
								"id": "idlogin"
							},
							{
								"type": "span",
								"style": "float:right;padding-right: 10px;",
								"contents": [
									{
										"type": "span",
										"style": "display: inline-block; padding-right: 30px; ",
										"id": "iddate"
									},
									{
										"type": "button",
										"id": "btnEdit",
										"style": "padding-left: 10px; padding-right:10px;",
										"innerHTML": "Editovat"
									}
								]
							}

						]
					},
					{
						"type": "div",
						"style": "display: block; width: 100%; padding-bottom: 50px;padding-left: 5px; padding-top: 5px;",
						"id": "ctext"
					}
				]
			}
		]
	};

	return self;
}