goog.provide("jimw_clj.something");

/* This file will be compiled by the ClojureScript compiler without
 * needing a foreign libs declaration in the build configuration. It's
 * position in the filesystem follows mirrors the its "namespace"
 * following the same conventions as ClojureScript.
 * Figwheel will hot reload this file as you save it.
*/

jimw_clj.something.hello = function() {return "Hey there from example.something JavaScript";}

jimw_clj.something.copyToClipboard = function (s) {
    document.addEventListener('copy', function(e){
        e.clipboardData.setData('text/plain', s);
        e.preventDefault();
    });
    document.execCommand('copy');
}

// <p class="p1">sam fff <a href="#">sam </a> te<span>samxx</span> dddn<span>sdsdddsamdsdds</span>eeds highlighting my text</p>
// 
// <p class="p2"> This is first appearance of word Hello This is second appearance of word Hello Yet another appearance of word Hello </p>
// 
// <input type="button" id="button" value="Highlight Selection" />

//var onClick = function() {
//    highlight("", "red");
//};

//$('#button').click(onClick);
var selectionRange;

function jimw_clj.something.highlight(highlightID, color) {
    if (window.getSelection && window.getSelection().toString()) {
        var node = getSelectionParentElement();
        if (node != null) {
	    var text = getSelectionText();
	    console.log("Selected text: " + text);
	    markFunc(node, text, /*HIGHLIGHT_CLASS + " " + */color);
        } else {
            console.log("Parent nde is null for some reason");
        }
    } else {
  	console.log("tapped without selection");
    }
}

function getSelectionText() {
    if (window.getSelection) {
        var sel = window.getSelection();
        return sel.toString();
    }
};

function getSelectionParentElement() {
    var parentEl = null,
        sel;
    if (window.getSelection) {
        sel = window.getSelection();
        if (sel.rangeCount) {
            selectionRange = sel.getRangeAt(0);
            parentEl = selectionRange.commonAncestorContainer;
            if (parentEl.nodeType != 1) {
                parentEl = parentEl.parentNode;
            }
        }
    } else if ((sel = document.selection) && sel.type != "Control") {
        parentEl = sel.createRange().parentElement();
    }
    return parentEl;
};

function markFunc(node, text, color) {
    var instance = new Mark(node);
    instance.mark(text, {
        "element": "span",
	"className": color,
	"acrossElements": true,
	"separateWordSearch": false,
	"accuracy": "partially",
	"diacritics": true,
	"ignoreJoiners": true,
        "each": function(element) {
            element.setAttribute("id", "sohayb");
            element.setAttribute("title", "sohayb_title");
	},
        "done":function(totalMarks) {
            window.getSelection().empty();//This only in Chrome
            console.log("total marks: " + totalMarks);
        },
	"filter": function(node, term, totalCounter, counter) {
            var res = false;
            if (counter == 0) {
        	res = selectionRange.isPointInRange(node, selectionRange.startOffset);
            } else {
                res = selectionRange.isPointInRange(node, 1);
            }
            console.log("Counter: " + counter + ", startOffset: " + selectionRange.startOffset);
            return res;
	}
    });
};

console.log("This is printed at load time from example.something JavaScript");
