
<!-- .navbar -->
<nav class="navbar navbar-inverse navbar-fixed-top"><#-- navbar-static-top -->
  <div class="container-fluid">
    <!-- Brand and toggle get grouped for better mobile display -->
    <header class="navbar-header">
        <button type="button" class="navbar-toggle" data-toggle="collapse" data-target=".navbar-ex1-collapse">
            <span class="sr-only">Toggle navigation</span>
            <span class="icon-bar"></span>
            <span class="icon-bar"></span>
            <span class="icon-bar"></span>
        </button>
        <#assign headerLogoList = sri.getThemeValues("STRT_HEADER_LOGO")>
        <#if headerLogoList?has_content><a href="${sri.buildUrl("/").getUrl()}" class="navbar-brand"><img src="${sri.buildUrl(headerLogoList?first).getUrl()}" alt="Home" height="50"></a></#if>
        <#assign headerTitleList = sri.getThemeValues("STRT_HEADER_TITLE")>
        <#if headerTitleList?has_content><div class="navbar-text">${ec.resource.expand(headerTitleList?first, "")}</div></#if>
    </header>
    <div id="navbar-buttons" class="collapse navbar-collapse navbar-ex1-collapse">
        <!-- .nav -->
        <ul id="header-menus" class="nav navbar-nav">
            <#-- NOTE: menu drop-downs are appended here using JS as subscreens render, so this is empty -->

            <#-- Alternate menu code, show menus/submenus instead of breadcrumb menus (the current approach):
            <#assign menuTitle = sri.getActiveScreenDef().getDefaultMenuName()!"Menu">
            <#assign menuUrlInfo = sri.buildUrl("")> ${menuUrlInfo.minimalPathUrlWithParams}
            <li class='dropdown'>
                <a href="#" class="dropdown-toggle" data-toggle="dropdown">${menuTitle}<b class="caret"></b></a>
                <ul class="dropdown-menu">
                <#list sri.getActiveScreenDef().getMenuSubscreensItems() as subscreensItem>
                    <#assign urlInfo = sri.buildUrl(subscreensItem.name)>
                    <#if urlInfo.isPermitted()>
                        <li class="<#if urlInfo.inCurrentScreenPath>active</#if>"><a href="<#if urlInfo.disableLink>#<#else>${urlInfo.minimalPathUrlWithParams}</#if>">${ec.l10n.localize(subscreensItem.menuTitle)}</a></li>
                    </#if>
                </#list>
                </ul>
            </li>
            -->
        </ul><!-- /.nav -->
        <div id="navbar-menu-crumbs"></div>
        <div class="navbar-text">${html_title!(ec.resource.expand(sri.screenUrlInfo.targetScreen.getDefaultMenuName()!"Page", ""))}</div>
        <#-- logout button -->
        <a href="${sri.buildUrl("/Login/logout").url}" data-toggle="tooltip" data-original-title="Logout ${(ec.getUser().getUserAccount().userFullName)!}" data-placement="bottom" class="btn btn-danger btn-sm navbar-btn navbar-right">
            <i class="glyphicon glyphicon-off"></i>
        </a>
        <#-- dark/light switch -->
        <a href="#" onclick="switchDarkLight();" data-toggle="tooltip" data-original-title="Switch Dark/Light" data-placement="bottom" class="btn btn-default btn-sm navbar-btn navbar-right">
            <i class="glyphicon glyphicon-adjust"></i>
        </a>
        <#-- header navbar items from the theme -->
        <#assign navbarItemList = sri.getThemeValues("STRT_HEADER_NAVBAR_ITEM")>
        <#list navbarItemList! as navbarItem>
            <#assign navbarItemTemplate = navbarItem?interpret>
            <@navbarItemTemplate/>
        </#list>
        <#-- screen history menu -->
        <#assign screenHistoryList = ec.web.getScreenHistory()>
        <ul id="history-menus" class="nav navbar-right">
            <li id="history-menu" class="dropdown">
                <a id="history-menu-link" href="#" class="dropdown-toggle btn btn-default btn-sm navbar-btn" data-toggle="dropdown" title="History">
                    <i class="glyphicon glyphicon-list"></i></a>
                <ul class="dropdown-menu"><#list screenHistoryList as screenHistory><#if (screenHistory_index >= 25)><#break></#if>
                    <li><a href="${screenHistory.url}">
                        <#if screenHistory.image?has_content>
                            <#if screenHistory.imageType == "icon">
                                <i class="${screenHistory.image}" style="padding-right: 8px;"></i>
                            <#elseif screenHistory.imageType == "url-plain">
                                <img src="${screenHistory.image}" width="18" style="padding-right: 4px;"/>
                            <#else>
                                <img src="${sri.buildUrl(screenHistory.image).url}" height="18" style="padding-right: 4px;"/>
                            </#if>
                        <#else>
                            <i class="glyphicon glyphicon-link" style="padding-right: 8px;"></i>
                        </#if>
                        ${screenHistory.name}
                    </a></li>
                </#list></ul>
            </li>
        </ul>
        <#-- dark/light switch JS method -->
        <script>
            $('#history-menu-link').tooltip({ placement:'bottom', trigger:'hover' });
            function switchDarkLight() {
                $("body").toggleClass("bg-dark dk");
                $("body").toggleClass("bg-light lter");
                var currentStyle = $("body").hasClass("bg-dark dk") ? "bg-dark dk" : "bg-light lter";
                $.ajax({ type:'POST', url:'/apps/setPreference', data:{ 'moquiSessionToken': '${ec.web.sessionToken}','preferenceKey': 'OUTER_STYLE', 'preferenceValue': currentStyle }, dataType:'json' });
            }
        </script>
    </div>
  </div> <!-- container-fluid -->
</nav><!-- /.navbar -->

<#-- A header below the navbar, commented as not used by default:
<header class="head">
    <div class="search-bar">
        <a data-original-title="Show/Hide Menu" data-placement="bottom" data-tooltip="tooltip" class="accordion-toggle btn btn-primary btn-sm visible-xs" data-toggle="collapse" href="#menu" id="menu-toggle">
            <i class="fa fa-expand"></i>
        </a>
        <form class="main-search">
            <div class="input-group">
                <input type="hidden" name="moquiSessionToken" value="${(ec.web.sessionToken)!}">
                <input type="text" class="input-small form-control" placeholder="Live Search ...">
                <span class="input-group-btn">
                    <button class="btn btn-primary btn-sm text-muted" type="button"><i class="fa fa-search"></i></button>
                </span>
            </div>
        </form>
    </div>
    <div class="main-bar">
        <h3>${html_title!((sri.screenUrlInfo.targetScreen.getDefaultMenuName())!"Page")}</h3>
    </div>
</header>
-->
