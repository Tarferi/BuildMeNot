var CommonFormats = function() {
	var self = this;
	
	self.supportedTags = ["math", "code", "code_line"];
	
	var formatMath = function(text) {
		var data = {"throwOnError": false};
		var html = katex.renderToString(text, data);
		return html;
	};
	
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
		}
		return text;
	}
	
	self.format = function(data) {
		var lastPos = 0;
		var indexes = [];
		data = data + "";
		for(var tagI = 0; tagI < self.supportedTags.length; tagI++) {
			var tag = self.supportedTags[tagI];
			var btag = "<" + tag + ">";
			var etag = "</" + tag + ">";
			lastPos = 0;
			while(true) {
				var pos = data.indexOf(btag, lastPos);
				if(pos == -1) {
					break;
				} else {
					var pos2 = data.indexOf(etag, pos);
					if(pos2 == -1) {
						break;
					} else {
						pos2 += etag.length;
						lastPos = pos2;
						indexes.push([pos, pos2, tag]);
					}
				}
			}
		}
		indexes = indexes.sort(function(aa, bb){
			var a = aa[0];
			var b = bb[0];
			return a < b ? 1 : a > b ? -1 : 0;
		});
		for(var i = 0; i < indexes.length; i++) {
			var posData = indexes[i];
			var begin = posData[0];
			var end = posData[1];
			var tag = posData[2];
			var btag = "<" + tag + ">";
			var etag = "</" + tag + ">";
			var length = (end - etag.length) - (begin + btag.length);
			
			var before = data.substr(0, begin);
			var after = data.substr(end);
			var contents = data.substr(begin + btag.length, length);
			
			var repl = formatByTag(tag, contents);
			
			data = before + repl + after;
		}
		
		return data;
	}

	self.init = function() {
		
	}


	self.init();
	return this;	
}