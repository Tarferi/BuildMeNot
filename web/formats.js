var CommonFormats = function() {
	var self = this;
	
	var supportedEmoji = ["monkaChrist"]
	
	var supportedTags = ["math", "code", "code_line"];
	
	supportedEmoji.map(function(e){supportedTags.push(e);});
	
	var formatMath = function(text) {
		var data = {"throwOnError": false};
		var html = katex.renderToString(text, data);
		return html;
	};
	
	var formatEmoji = function(text) {
		var el = document.createElement("img");
		el.src = "https://rion.cz/icons/" + text + ".png";
		return el
	}
	
	var formatCode = function(text) {
		var el = document.createElement("span");
		el.style.display = "inline-block";
		el.style.marginTop = "8px";
		el.style.paddingLeft = "5px";
		el.style.paddingRight = "5px";
		el.style.background = "#eeeeee";
		el.style.border = "1px solid #cccccc";
		el.style.fontFamily = "Courier, \"Lucida Console\", monospace";
		el.innerHTML = text;
		return el.outerHTML;
	}
	
	var formatCodeLine = function(text) {
		var el = document.createElement("span");
		el.style.display = "block";
		el.style.marginTop = "8px";
		el.style.marginBottom = "8px";
		el.style.marginRight = "5px";
		el.style.paddingTop = "5px";
		el.style.paddingLeft = "5px";
		el.style.paddingBottom = "5px";
		el.style.background = "#eeeeee";
		el.style.border = "1px solid #cccccc";
		el.style.fontFamily = "Courier";
		el.innerHTML = text;
		return el.outerHTML;
	}
	
	var formatByTag = function(tag, text) {
		if(tag == "math") {
			return formatMath(text);
		} else if(tag == "code") {
			return formatCode(text);
		} else if(tag == "code_line") {
			return formatCodeLine(text);
		} else if(supportedEmoji.indexOf(tag) >= 0) {
			return formatEmoji(text);
		}
		return text;
	}
	
	self.format = function(data) {
		var allowedLts = [];
		var allowedGts = [];
		var getIndexes = function(data) {
			var lastPos = 0;
			var indexes = [];
			data = data + "";
			for(var tagI = 0; tagI < supportedTags.length; tagI++) {
				var tag = supportedTags[tagI];
				var btag = "<" + tag + ">";
				var etag = "</" + tag + ">";
				var ttag = "<" + tag + "/>";
				lastPos = 0;
				while(true) {
					var pos = data.indexOf(btag, lastPos);
					if(pos == -1) {
						pos = data.indexOf(ttag, lastPos);
						if(pos == -1) {
							break;
						} else {
							lastPos = pos + ttag.length;
							indexes.push([pos, pos + ttag.length, tag, 1]);
							allowedLts.push(pos);
							allowedGts.push(pos + tag.length + 2);
						}
					} else {
						var pos2 = data.indexOf(etag, pos);
						if(pos2 == -1) {
							break;
						} else {
							pos2 += etag.length;
							lastPos = pos2;
							indexes.push([pos, pos2, tag, 0]);
							
							allowedLts.push(pos);
							allowedGts.push(pos + tag.length + 1);
							allowedLts.push(pos2 - tag.length - 3);
							allowedGts.push(pos2 - 1);
						}
					}
				}
			}
			return [indexes, allowedLts, allowedGts];
		}
		
		var replaceIndexes = function(data, indexes, generate) {
			// Sort the array
			indexes = indexes.sort(function(aa, bb) {
				var a = aa[0];
				var b = bb[0];
				return a < b ? 1 : a > b ? -1 : 0;
			});
			
			// Replace from the last char
			for(var i = 0; i < indexes.length; i++) {
				var posData = indexes[i];
				var begin = posData[0];
				var end = posData[1];
				var tag = posData[2];
				var type = posData[3];
				if(type == 0 && generate) {
					var btag = "<" + tag + ">";
					var etag = "</" + tag + ">";
					var length = (end - etag.length) - (begin + btag.length);
					
					var before = data.substr(0, begin);
					var after = data.substr(end);
					var contents = data.substr(begin + btag.length, length);
					
					var repl = formatByTag(tag, contents);
					
					data = before + repl + after;
				} else if(type == 1 && generate) {
					var before = data.substr(0, begin);
					var after = data.substr(end);
					var contents = tag;
					
					var repl = formatByTag(tag, contents);
					
					data = before + repl.outerHTML + after;
				} else if(type == 2 && !generate) {
					var repl = posData[4];
					
					var before = data.substr(0, begin);
					var after = data.substr(end);
					
					data = before + repl + after;
				}
			}
			return data;
		}
				
		var appendSpecialChars = function(chr, repl, excs) {
			var lastIndex = 0;
			while(true) {
				var index = data.indexOf(chr, lastIndex);
				if(index == -1) {
					break;
				} else {
					if(excs.indexOf(index) == -1) { // Not known tag
						indexes.push([index, index + 1, chr, 2, repl]);
					}
					lastIndex = index + 1;
				}
			}
		}
		
		var d = getIndexes(data);
		var indexes = d[0];
		var allowedLts = d[1];
		var allowedGts = d[2]; 

		appendSpecialChars("<", "&lt;", allowedLts);
		appendSpecialChars(">", "&gt;", allowedGts);
		
		data = replaceIndexes(data, indexes, false);
		
		d = getIndexes(data);
		indexes = d[0];
		
		data = replaceIndexes(data, indexes, true);
		
		return data;
	}

	self.init = function() {
		
	}


	self.init();
	return this;	
}