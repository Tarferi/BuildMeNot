if(!window.Adminer) {
	window.Adminer = {}
}

window.Adminer.Templates = function() {
	var self = this;
	
	self.nagivationItemUI =  {
		"type": "div",
		"style": "display: block; width: 100%; height: 40px; line-height: 40px",
		"class": "adm_file_wrapper",
		"contents": [
			{
				"type": "img",
				"style": "display: inline-block; width: 30px; height: 30px; margin: 5px",
				"id": "img"
			},
			{
				"type": "span",
				"style": "position: relative; left: 5px; top: -12px; font-family: Tahoma; font-size: 12pt;",
				"innerHTML": "ABC",
				"id": "lbl"										
			}
		]	
	};
	
	self.mainUI = {
		"type": "div",
		"style": "display: block; position: fixed; left: 0px; right: 0px; top: 0px; bottom: 0px;background: white; font-family: verdana; font-size: 13pt;",
		"contents": [
			{
				"type": "div",
				"style": "border-bottom: 1px solid black; display: block; height: 35px; width: 100%; text-align: right;",
				"contents": [
					{
						"type": "button",
						"style": "margin-top: 5px; margin-right: 5px;",
						"innerHTML": "Zavřít",
						"id": "btnCloseAdmin"
					}
				]
			},
			{
				"type": "table",
				"style": "border-collapse:collapse; width: 100%; height: calc(100% - 35px); max-height: calc(100% - 35px); border: 0px solid black",
				"contents": [
					{
						"type": "tbody",
						"style": "height: 100%;",
						"contents": [
							{
								"type": "tr",
								"contents": [
									{
										"type": "td",
										"style": "width: 400px; height: 100%; border-right: 1px solid black; vertical-align: top; padding: 0px; margin: 0px;",
										"contents": [
											{
												"type": "table",
												"style": "border-collapse: collapse; width: 100%; height: 100%; padding: 0px; margin: 0px;",
												"contents": [
													{
														"type": "tbody",
														"contents": [
															{
																"type": "tr",
																"style": "height: 35px;",
																"contents": [
																	{
																		"type": "td",
																		"style": "border-bottom: 1px solid black; text-align: center;",
																		"contents": [
																			{
																				"type": "button",
																				"style": "",
																				"innerHTML": "Reload",
																				"id": "btnReload"
																			}
																		]
																	}
																]
															},
															{
																"type": "tr",
																"style": "height: 30px;",
																"contents": [
																	{
																		"type": "td",
																		"style": "text-align: center; line-height: 35px; border-bottom: 1px solid black;",
																		"id": "pnlCurrentPath"
																	}
																]
															},
															{
																"type": "tr",
																"contents": [
																	{
																		"type": "td",
																		"style": "text-align: center; line-height: 35px;",
																		"contents": [
																			{
																				"type":"div",
																				"style": "position: absolute; top: 110px; width: 398px; bottom: 50px; overflow-y: scroll; text-align: left;",
																				"id": "filesPnl"
																			}
																		]
																	}
																]
															},
															{
																"type": "tr",
																"style": "height: 50px;",
																"contents": [
																	{
																		"type": "td",
																		"style": "border-top: 1px solid black;",
																		"contents": [
																			{
																				"type": "input",
																				"style": "padding-left: 5px; display: inline-block; width: calc(100% - 100px); margin-right: 8px; margin-left: 5px; height: 23px",
																				"id": "txtNewFile"
																			},
																			{
																				"type": "button",
																				"style": "position:relative; top: 1px; padding-left:10px; padding-top: 2px; padding-bottom: 2px; padding-right: 10px; height: 28px;",
																				"innerHTML": "Create",
																				"id": "adm_td_main_left_bottom_nf_btn"
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
										"style": "width: calc(100% - 400px); height: 100%; vertical-align: top; padding: 0px; margin: 0px;",
										"contents": [
											{
												"type": "table",
												"style": "border-collapse: collapse; width: 100%; height: 100%; padding: 0px; margin: 0px;",
												"contents": [
													{
														"type": "tbody",
														"contents": [
															{
																"type": "tr",
																"style": "height: 35px;",
																"contents": [
																	{
																		"type": "td",
																		"style": "text-align: center; line-height: 35px; border-bottom: 1px solid black;font-family: Tahoma; font-size: 13pt; font-style: italic;text-decoration: underline;",
																		"id": "adm_right_top_lbl",
																		"innerHTML": "/path"
																	}
																]
															},
															{
																"type": "tr",
																"contents": [
																	{
																		"type": "td",
																		"style": "text-align: center; line-height: 35px;",
																		"contents": [
																			{
																				"type":"div",
																				"style": "position: absolute; top: 75px; width: calc(100% - 402px); bottom: 50px; overflow-y: auto",
																				"id": "pnlContents",
																				"contents": [
																		             {
																	                  "type": "table",
																	                  "class": "adm_content_table",
																	                  "id": "adm_content_table"
																	                },
																	                {
																	                  "type": "textarea",
																	                  "class": "adm_content_editble",
																	                  "id": "adm_content_editble"
																	                },
																	                {
																	                  "type": "div",
																	                  "id": "adm_content_details",
																	                  "class": "adm_content_details"
																	                }
																				]
																			}
																		]
																	}
																]
															},
															{
																"type": "tr",
																"style": "height: 50px;",
																"contents": [
																	{
																		"type": "td",
																		"style": "border-top: 1px solid black; text-align: right;",
																		"contents": [
																			{
																				"type": "button",
																				"style": "padding-left:10px; padding-top: 2px; padding-bottom: 2px; padding-right: 10px; height: 28px; margin-right: 10px;",
																				"innerHTML": "Return",
																				"id": "btnReturn"
																			},
																			{
																				"type": "button",
																				"style": "padding-left:10px; padding-top: 2px; padding-bottom: 2px; padding-right: 10px; height: 28px; margin-right: 10px;",
																				"innerHTML": "View",
																				"id": "btnEditView"
																			},
																			{
																				"type": "button",
																				"style": "padding-left:10px; padding-top: 2px; padding-bottom: 2px; padding-right: 10px; height: 28px; margin-right: 10px;",
																				"innerHTML": "Close",
																				"id": "btnClose"
																			},
																			{
																				"type": "button",
																				"style": "padding-left:10px; padding-top: 2px; padding-bottom: 2px; padding-right: 10px; height: 28px; margin-right: 10px;",
																				"innerHTML": "Save",
																				"id": "btnSave"
																			},
																			{
																				"type": "button",
																				"style": "padding-left:10px; padding-top: 2px; padding-bottom: 2px; padding-right: 10px; height: 28px; margin-right: 10px;",
																				"innerHTML": "Save and close",
																				"id": "btnSaveClose"
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
					}
				]
			}
		]
	}
	
	
	
	
	return this;
}