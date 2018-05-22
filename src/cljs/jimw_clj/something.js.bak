goog.provide("jimw_clj.something");

/* This file will be compiled by the ClojureScript compiler without
 * needing a foreign libs declaration in the build configuration. It's
 * position in the filesystem follows mirrors the its "namespace"
 * following the same conventions as ClojureScript.
 * Figwheel will hot reload this file as you save it.
*/

jimw_clj.something.hello = function() {return "Hey there from example.something JavaScript";}


jimw_clj.something.getSelectionRange = function() {
  var sel = document.selection, range = null;
  var width = 0, height = 0, left = 0, top = 0;
  if (sel) {
    if (sel.type != "Control") {
      range = sel.createRange();
    }
  } else if (window.getSelection) {
    sel = window.getSelection();
    if (sel.rangeCount) {
      range = sel.getRangeAt(0).cloneRange();
    }
  }
  return range;
}

jimw_clj.something.isSelectionInFormElement = function () {
  var selection_element = document.activeElement;
  if (!selection_element)
    return false;
  var tag_name = selection_element.tagName.toLowerCase();
  return tag_name == 'input' || tag_name == 'textarea' || tag_name == 'select' || tag_name == 'button';
}

jimw_clj.something.getSelectionEndPosition = function () {
  if (false) {
    if (window.pdfSelectTextRect &&
        window.pdfSelectTextRect.upX &&
        window.pdfSelectTextRect.upY) {
      var res = {
          x: window.pdfSelectTextRect.upX,
          y: window.pdfSelectTextRect.upY
      };
      if (window.pdfSelectTextRect.downX && window.pdfSelectTextRect.downY) {
        res.x = Math.max(window.pdfSelectTextRect.downX,
            window.pdfSelectTextRect.upX);
        res.y = Math.max(window.pdfSelectTextRect.downY,
            window.pdfSelectTextRect.upY);
      }
      return res;
    }
  }
  var range = jimw_clj.something.getSelectionRange();
  if (!range)
    return null;
  var sel = document.selection;
  var x = 0, y = 0;
  if (sel) {
    if (sel.type != "Control") {
    }
  } else if (window.getSelection) {
    if (jimw_clj.something.isSelectionInFormElement()) {
      var selection_element = document.activeElement;
      if (!selection_element)
        return { x: x, y: y };
      var selection_start = selection_element.selectionStart;
      var selection_end = selection_element.selectionEnd;
      var form_element_selection_rect = getTextBoundingRect(
          selection_element, selection_start, selection_end, false);
      x = form_element_selection_rect.right;
      y = form_element_selection_rect.bottom;
      return { x: x, y: y };
    }
    
    var endNode = range.endContainer;
    var endRange = document.createRange();
    endRange.setStart(endNode, 0);
    endRange.setEnd(endNode, range.endOffset);
    var endRangeRect = endRange.getClientRects()[endRange.getClientRects().length - 1];
    var ebdRangeRect2;
    if (endRange.getClientRects().length > 1) {
      endRangeRect2 = endRange.getClientRects()[endRange.getClientRects().length - 2];
    }
    if (endRangeRect.left === endRangeRect.right && endRangeRect2) {
      x = endRangeRect2.right;
      y = endRangeRect2.bottom;
    } else {
      x = endRangeRect.right;
      y = endRangeRect.bottom;
    }
  }
  return { x: x, y: y };
}


console.log("This is printed at load time from example.something JavaScript");
