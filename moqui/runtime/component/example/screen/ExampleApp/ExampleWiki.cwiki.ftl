
{toc}

h1. Example Wiki

This is an example wiki page using the Confluence style of wiki markup (hence the extension cwiki).

A reference for the syntax is available here: [Confluence Notation Guide|http://confluence.atlassian.com/renderer/notationhelp.action?section=all]

If you are reading this in your browser you are probably viewing this in an HTML form which was created by transforming
the wiki text to HTML using the Mylyn WikiText library.

* Bullet 1
** Bullet 1.1
** Bullet 1.2
* Bullet 2
*# Bullet 2 Number 1
*# Bullet 2 Number 2

h2. Sub-Heading 1

This is the text under Sub-Heading 1.

h2. Sub-Heading 2

This is the text under Sub-Heading 2.

h2. FTL Markup Supported

This content is also dynamic and the normal context is available here.

For example, here is your userId: ${ec.user.userId?default("(No User Logged In)")}

h2. Child Pages

If this doesn't work through WikiText (probably won't) then we'll add something so that FTL interpretation gets it with
something like the OFBiz ContentMapFacade:

{children}
