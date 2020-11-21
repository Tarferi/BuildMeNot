
if(!window.Projecter) {
	window.Projecter = {}
}

window.Projecter.Templates = function() {
	var self = this;
	
	var btnStyle = "height: 30px; padding-left: 20px; padding-right: 20px; margin-left: 5px; margin-right: 5px;";

	self.FeedbackLoadedRowUI =  {
		"type": "tr",
		"style": "border-bottom: 1px solid black",
		"contents": [
			{
				"type": "td",
				"contents": [
					{
						"type": "div",
						"style": "display: block;background:#aaffdd;line-height: 30px;border-bottom: 1px solid black;",
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
						"style": "display: block; padding-bottom: 50px;padding-left: 5px; padding-top: 5px;",
						"id": "ctext"
					}
				]
			}
		]
	};
	
	self.commentUI = { 
		"type": "div",
		"style": "display: inline-block; position: absolute; right: calc(5% + 10px); top: 220px; height: 555px; overflow: auto; width: 420px; overflow-y: scroll",
		"contents": [
			{		
				"type": "table",
				"style": "border-collapse: collapse; border-top: 0px; margin: 0px; padding: 0px; float: right",
				"contents": [
					{
						"type": "tbody",
						"contents": [
							{
								"type":"tr",
								"contents": [
									{
										"type": "td",
										"style": "border-bottom: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 100%",
										"innerHTML": "Komentáře"
									}
								]
							},
							{
								"type": "tr",
								"contents": [
									{
										"type": "td",
										"style": "border-bottom: 1px solid black",
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
										"style": "border-bottom: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 200px;",
										"innerHTML": "Označení"
									}
								]
							},
							{
								"type": "tr",
								"contents": [
									{
										"type": "td",
										"style": "border-bottom: 1px solid black; padding: 10px; vertical-align: top;",
										"id": "commentSel"
									}
								]
							},
							{
								"type": "tr",
								"contents": [
									{
										"type": "td",
										"style": "border-bottom: 1px solid black; height: 40px; background-color: #aaddee;margin: 0px; padding: 0px;text-align: center;line-height: 40px; font-size: 20pt;width: 178px;min-width:178px",
										"innerHTML": "Akce"
									}
								]
							},
							{
								"type": "tr",
								"contents": [
									{
										"type": "td",
										"style": "border-bottom: 1px solid black; min-height: 40px;",
										"contents": [
											{
												"type": "textarea",
												"style": "outline: none; border: none; width: 396px; height: 80px; resize: vertical",
												"id": "commentEditorNode"										
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
										"style": "vertical-align: top; text-align: right;padding-right: 5px; padding-top: 5px; padding-bottom: 5px;",
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
			}
		]
	}
	
	
	var getCard = function(idPrefix, name, firstCard, lastCard) {
		return {
			"type": "div",
			"id": idPrefix + "cardBtn",
			"style": "height: 30px; text-align: center; background-color: #ffeeee; cursor:pointer; display: inline-block; width: 200px;line-height: 30px;padding-left: 10px; padding-right: 10px;" + (firstCard ? "" :"border-left: 1px solid black;") + (lastCard ? "border-right: 1px solid black;" : ""),
			"innerHTML": name
		};
	}
	
	var cardSettingsHeaderCellStyle = "text-align: right; width: 200px; padding-right: 10px; background: #efefef;padding-top: 3px; padding-bottom: 3px;";
	
	self.cardSettings = {
		"type": "div",
		"id": "card_set_pnl",
		"style": "",
		"contents": [
			{
					"type": "table",
					"style": "border-collapse: collapse; width: 100%; font-family: verdana; font-size: 12pt;margin: 0px; padding: 0px;",
					"contents": [
						{
							"type": "tbody",
							"contents": [
								{
									"type": "tr",
									"contents": [
										{
											"type": "td",
											"style": cardSettingsHeaderCellStyle + "border-right: 1px solid black;border-bottom: 1px solid black",
											"innerHTML": "Název projektu"
										},
										{
											"type": "td",
											"style": "margin-right: 10px; border-bottom: 1px solid black",
											"contents": [
												{
													"type": "input",
													"id": "txtName",
													"style": "width: calc(100% - 20px); padding: 5px; border: 0px; outline: none;"
												}
											]
										}
									]
								},
								{
									"type": "tr",
									"style": "border-bottom: 1px solid black;",
									"contents": [
										{
											"type": "td",
											"style": cardSettingsHeaderCellStyle + "border-right: 1px solid black; vertical-align: top",
											"innerHTML": "Popis projektu"
										},
										{
											"type": "td",
											"style": "margin-right: 10px;",
											"contents": [
												{
													"type": "textarea",
													"id": "txtDescr",
													"style": "width: calc(100% - 20px); min-height: 300px; padding: 5px; border: 0px; outline: none; resize: vertical;"
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
											"style": cardSettingsHeaderCellStyle + "border-right: 1px solid black; border-bottom: 1px solid black;",
											"innerHTML": "Prefix souborů"
										},
										{
											"type": "td",
											"style": "margin-right: 10px; border-bottom: 1px solid black",
											"contents": [
												{
													"type": "input",
													"id": "txtPrefix",
													"style": "width: calc(100% - 20px); padding: 5px; border: 0px; outline: none;"
												}
											]
										}
									]
								},
								{
									"type": "tr",
									"style": "border-bottom: 1px solid black;",
									"contents": [
										{
											"type": "td",
											"style": cardSettingsHeaderCellStyle + "border-right: 1px solid black; vertical-align: top; ",
											"innerHTML": "Odevzdávané soubory"
										},
										{
											"type": "td",
											"style": "margin-right: 10px;",
											"contents": [
												{
													"type": "textarea",
													"id": "txtFiles",
													"style": "width: calc(100% - 20px); min-height: 300px; padding: 5px; border: 0px; outline: none; resize: vertical;"
												}
											]
										}
									]
								},
								{
									"type": "tr",
									"style": "border-bottom: 1px solid black;",
									"contents": [
										{
											"type": "td",
											"style": cardSettingsHeaderCellStyle + "border-right: 1px solid black; vertical-align: top",
											"innerHTML": "Editory pro soubory"
										},
										{
											"type": "td",
											"style": "margin-right: 10px;",
											"contents": [
												{
													"type": "textarea",
													"id": "txtEditors",
													"style": "width: calc(100% - 20px); min-height: 300px; padding: 5px; border: 0px; outline: none; resize: vertical;"
												}
											]
										}
									]
								},
								{
									"type": "tr",
									"style": "border-bottom: 1px solid black;",
									"contents": [
										{
											"type": "td",
											"style": cardSettingsHeaderCellStyle + "border-right: 1px solid black; vertical-align: top",
											"innerHTML": "Komentovatelné soubory"
										},
										{
											"type": "td",
											"style": "margin-right: 10px;",
											"contents": [
												{
													"type": "textarea",
													"id": "txtCommentFiles",
													"style": "width: calc(100% - 20px); min-height: 300px; padding: 5px; border: 0px; outline: none; resize: vertical;"
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
											"style": cardSettingsHeaderCellStyle + "border-right: 1px solid black; vertical-align: top",
											"innerHTML": "Soubory viditelné studentům"
										},
										{
											"type": "td",
											"style": "margin-right: 10px;",
											"contents": [
												{
													"type": "textarea",
													"id": "txtVisibleFiles",
													"style": "width: calc(100% - 20px); min-height: 300px; padding: 5px; border: 0px; outline: none; resize: vertical;"
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
											"colSpan": 2,
											"style": "text-align: right; border-top: 1px solid black;padding-top: 5px; padding-bottom: 5px; padding-right: 5px;",
											"contents": [
												{
													"type": "button",
													"style": "width: 80px; height: 27px;",
													"id": "btnSave",
													"innerHTML": "Uložit"
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
	
	var cardResultsLeftPanelWidth = 250;
	
	
	self.cardResults = {
		"type": "div",
		"id": "card_res_pnl",
		"style": "",
		"contents": [
			{
				"type": "table",
				"style": "border-collapse: collapse; width: 100%; font-family: verdana; font-size: 12pt;margin: 0px; padding: 0px",
				"contents": [
					{
						"type": "thead",
						"contents": [
							{
								"type": "tr",
								"contents": [
									{
										"type": "th",
										"style": "width: " + cardResultsLeftPanelWidth + "px; margin: 0px; padding: 0px; border-bottom: 1px solid black;padding-top: 10px; padding-bottom: 10px;",
										"innerHTML": "Soubory"
									},
									{
										"type": "th",
										"style": "border-left: 1px solid black; margin: 0px; padding: 0px; border-bottom: 1px solid black",
										"innerHTML": "Data"
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
								"contents": [
									{
										"type": "td",
										"style": "border-right: 1px solid black",
										"contents": [
											{
												"type": "div",
												"style": "display: block; width: 100%; height: 600px; overflow-y: scroll",
												"id": "pnlContent"
											}
										]
									},
									{
										"type": "td",
										"style": "width: calc(100% - " + cardResultsLeftPanelWidth + "px); max-width: calc(100% - " + cardResultsLeftPanelWidth + "px); overflow: auto; height: 100%; vertical-align: top;",
										"contents": [
											{
												"type": "div",
												"style": "border-bottom: 1px solid black;",
												"id": "pnlTabs"
											},
											{
												"type": "div",
												"style": "position: absolute; left: calc(5% + " + (10 + cardResultsLeftPanelWidth) + "px); right: calc(5% + 10px); top: 220px; height: 555px; overflow: auto;overflow-wrap: anywhere; ",
												"id": "pnlEditors"
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
	
	self.cardInputsExistingUI = {
		"type": "tr",
		"contents": [
			{
				"type": "td",
				"style": "padding-top: 5px; padding-bottom: 5px; padding-left: 10px; border-right: 1px solid black",
				"id": "txtStudent"
			},
			{
				"type": "td",
				"style": "padding-top: 5px; padding-bottom: 5px; padding-right: 10px; text-align: right",
				"id": "txtUploader"
			}
		]
	}
	
	self.cardInputs = {
		"type": "div",
		"id": "card_io_pnl",
		"style": "",
		"contents": [
			{
				"type": "table",
					"style": "border-collapse: collapse; width: 100%; font-family: verdana; font-size: 12pt;margin: 0px; padding: 0px",
					"contents": [
						{
							"type": "thead",
							"contents": [
								{
									"type": "tr",
									"contents": [
										{
											"type": "th",
											"style": "width: 400px; margin: 0px; padding: 0px; border-bottom: 1px solid black;padding-top: 10px; padding-bottom: 10px;",
											"innerHTML": "Existující soubory"
										},
										{
											"type": "th",
											"style": "border-left: 1px solid black; margin: 0px; padding: 0px; border-bottom: 1px solid black",
											"innerHTML": "Nahrát soubory"
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
									"contents": [
										{
											"type": "td",
											"style": "margin: 0px; padding: 0px; min-height: 400px; vertical-align: top;",
											"contents": [
												{
													"type": "div",
													"style": "display: block; width: 100%; height: 400px; overflow: auto",
													"contents": [
														{
															"type": "table",
															"style": "border-collapse: collapse; width: 100%; height: 100%; font-family: verdana; font-size: 12pt;margin: 0px; padding: 0px;",
															"contents": [
																{
																	"type": "thead",
																	"contents": [
																		{
																			"type": "tr",
																			"contents": [
																				{
																					"type": "th",
																					"style": "border-right: 1px solid black; border-bottom: 1px solid black;padding-top: 10px; padding-bottom: 10px;",
																					"innerHTML": "Soubor studenta"	
																				},
																				{
																					"type": "th",
																					"style": "width: 150px;border-bottom: 1px solid black",
																					"innerHTML": "Nahrál"	
																				}
																			]
																		}
																	]
																},
																{
																	"type": "tbody",
																	"id":"pnlExisting"
																}
															]
														}
													]
												}
											]
										},
										{
											"type": "td",
											"style": "border-left: 1px solid black; text-align: center; vertical-align: middle; padding-top: 10%;",
											"id": "pnlUpload",
											"contents": [
												{
													"type":"div",
													"style": "margin: 10px; min-height: 200px;",
													"innerHTML": "Sem přetáhněte soubory",
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
	
	self.projectUI = {
		"type": "table",
		"style": "border-collapse: collapse; width: 90%; margin-left: 5%; border: 2px solid black; font-family: verdana; font-size: 12pt;",
		"contents": [
			{
				"type": "tbody",
				"contents": [
					{
						"type": "tr",
						"contents": [
							{
								"type": "td",
								"style": "height: 40px; background-color:#eaeaea; font-size: 15pt; padding-left: 10px;",
								"id": "pnlTitle"
							}
						]
					},
					{
						"type": "tr",
						"contents": [
							{
								"type": "td",
								"style": "height: 30px; background-color: #ffffff; border-top:2px solid black",
								"contents": [
									getCard("card_set_", "Nastavení", true, false),
									getCard("card_res_", "Výsledky", false, false),
									getCard("card_io_", "Vstupy", false, true)
								]
							}
						]
					},
					{
						"type": "tr",
						"contents": [
							{
								"type": "td",
								"style": "border-top: 2px solid black;margin: 0px; padding: 0px;",
								"id": "pnls"
							}
						]
					}
				]
			}
		]
	}
	
	
	
	return self;
}