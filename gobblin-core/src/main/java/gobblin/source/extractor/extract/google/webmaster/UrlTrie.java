package gobblin.source.extractor.extract.google.webmaster;

import java.util.Collection;
import java.util.TreeMap;


public class UrlTrie {
  private UrlTrieNode _root;
  private final String _prefix;

  /**
   * @param rootPage use the longest common prefix as your _root page.
   *                 e.g. if your pages are "www.linkedin.com/in/", "www.linkedin.com/jobs/", "www.linkedin.com/groups/"
   *                 The longest common prefix is "www.linkedin.com/", and it will be your _root page.
   *                 And the last "/" will be used as a TrieRoot.
   * @param pages
   */
  public UrlTrie(String rootPage, Collection<String> pages) {
    if (rootPage == null || rootPage.isEmpty()) {
      _prefix = null;
      _root = new UrlTrieNode(null);
    } else {
      _prefix = rootPage.substring(0, rootPage.length() - 1);
      Character lastChar = rootPage.charAt(rootPage.length() - 1);
      _root = new UrlTrieNode(lastChar);
    }
    for (String page : pages) {
      add(page);
    }
  }

  public UrlTrie(String rootPage, UrlTrieNode root) {
    _root = root;
    if (rootPage == null || rootPage.isEmpty()) {
      _prefix = null;
    } else {
      _prefix = rootPage.substring(0, rootPage.length() - 1);
    }
  }

  public void add(String page) {
    if (_prefix == null || _prefix.isEmpty()) {
      _root.add(page);
    } else {
      if (!page.startsWith(_prefix)) {
        throw new IllegalArgumentException(
            String.format("Found a page '%s' not starting with the root page '%s'", page, _prefix));
      }
      _root.add(page.substring(_prefix.length() + 1)); //1 comes from the last char in root.
    }
  }

  public UrlTrieNode getChild(String path) {
    return _root.getChild(path);
  }

  public UrlTrieNode getRoot() {
    return _root;
  }

  public String getPrefix() {
    return _prefix;
  }
}

class UrlTrieNode {
  public TreeMap<Character, UrlTrieNode> children = new TreeMap<>();  //immediate, first level children.
  private Character _value;
  private boolean _exist = false;
  //the count/size for all nodes with actual values/pages starting from itself and include all children, grand-children, etc...
  private long _size = 0;

  public UrlTrieNode(Character value) {
    _value = value;
  }

  public void add(String path) {
    UrlTrieNode parent = this;
    parent.increaseCount();
    for (int i = 0; i < path.length(); ++i) {
      Character c = path.charAt(i);
      UrlTrieNode child = parent.children.get(c);
      if (child == null) {
        child = new UrlTrieNode(c);
        parent.children.put(c, child);
      }
      child.increaseCount();
      parent = child;
    }
    parent._exist = true;
  }

  public UrlTrieNode getChild(String path) {
    UrlTrieNode parent = this;
    for (int i = 0; i < path.length(); ++i) {
      Character c = path.charAt(i);
      UrlTrieNode child = parent.children.get(c);
      if (child == null) {
        return null;
      }
      parent = child;
    }
    return parent;
  }

//  public UrlTrieNode nextSibling() {
//    if (_parent == null) {
//      return null;
//    }
//    Map.Entry<Character, UrlTrieNode> sibling = _parent.children.higherEntry(_value);
//    if (sibling == null) {
//      return null;
//    }
//    return sibling.getValue();
//  }

  public Character getValue() {

    return _value;
  }

  public boolean isExist() {
    return _exist;
  }

  public long getSize() {
    return _size;
  }

  public void increaseCount() {
    ++_size;
  }

  @Override
  public String toString() {
    return "UrlTrieNode{" + "_value=" + _value + ", _exist=" + _exist + ", _size=" + _size + '}';
  }
}