<!-- .navbar -->
<nav class="navbar navbar-inverse navbar-fixed-top"><#-- navbar-static-top -->

    <!-- Brand and toggle get grouped for better mobile display -->
    <header class="navbar-header">
        <button type="button" class="navbar-toggle" data-toggle="collapse" data-target=".navbar-ex1-collapse">
            <span class="sr-only">Toggle navigation</span>
            <span class="icon-bar"></span>
            <span class="icon-bar"></span>
            <span class="icon-bar"></span>
        </button>
        <#-- ${(ec.getTenant().tenantName)!"Welcome to Moqui"} -->
        <#-- <a href="index.html" class="navbar-brand"><img src="assets/img/logo.png" alt=""></a> -->
    </header>
    <div class="topnav">
        <div class="btn-toolbar">
            <div class="btn-group">
                <a data-placement="bottom" data-original-title="Show / Hide Sidebar" data-toggle="tooltip" class="btn btn-success btn-sm" id="changeSidebarPos">
                    <i class="fa fa-expand"></i>
                </a>
            </div>
            <#if ec.getUser().getUserId()?has_content>
                <#--
                <div class="btn-group">
                    <a data-placement="bottom" data-original-title="E-mail" data-toggle="tooltip" class="btn btn-default btn-sm">
                        <i class="fa fa-envelope"></i>
                        <span class="label label-warning">5</span>
                    </a>
                    <a data-placement="bottom" data-original-title="Messages" href="#" data-toggle="tooltip" class="btn btn-default btn-sm">
                        <i class="fa fa-comments"></i>
                        <span class="label label-danger">4</span>
                    </a>
                </div>
                <div class="btn-group">
                    <a data-placement="bottom" data-original-title="Document" href="#" data-toggle="tooltip" class="btn btn-default btn-sm">
                        <i class="fa fa-file"></i>
                    </a>
                    <a data-toggle="modal" data-original-title="Help" data-placement="bottom" class="btn btn-default btn-sm" href="#helpModal">
                        <i class="fa fa-question"></i>
                    </a>
                </div>
                -->
                <div class="btn-group">
                    <a href="/Login/logout" data-toggle="tooltip" data-original-title="Logout ${(ec.getUser().getUserAccount().userFullName)!!}" data-placement="bottom" class="btn btn-metis-1 btn-sm">
                        <i class="fa fa-power-off"></i>
                    </a>
                </div>
            </#if>
        </div>
    </div><!-- /.topnav -->
    <div class="collapse navbar-collapse navbar-ex1-collapse">
        <!-- .nav -->
        <ul id="header-menus" class="nav navbar-nav">
            <#--
            <#assign menuTitle = sri.getActiveScreenDef().getDefaultMenuName()!"Menu">
            <#-- <#assign menuUrlInfo = sri.buildUrl("")> ${menuUrlInfo.minimalPathUrlWithParams} - ->
            <li class='dropdown'>
                <a href="#" class="dropdown-toggle" data-toggle="dropdown">${menuTitle}<b class="caret"></b></a>
                <ul class="dropdown-menu">
                <#list sri.getActiveScreenDef().getSubscreensItemsSorted() as subscreensItem><#if subscreensItem.menuInclude>
                    <#assign urlInfo = sri.buildUrl(subscreensItem.name)>
                    <#if urlInfo.isPermitted()>
                        <li class="<#if urlInfo.inCurrentScreenPath>active</#if>"><a href="<#if urlInfo.disableLink>#<#else>${urlInfo.minimalPathUrlWithParams}</#if>">${ec.l10n.getLocalizedMessage(subscreensItem.menuTitle)}</a></li>
                    </#if>
                </#if></#list>
                </ul>
            </li>
            -->
            <#--
            <li> <a href="dashboard.html">Dashboard</a>  </li>
            <li> <a href="table.html">Tables</a>  </li>
            <li> <a href="file.html">File Manager</a>  </li>
            <li class='dropdown active'>
                <a href="#" class="dropdown-toggle" data-toggle="dropdown">
                    Form Elements
                    <b class="caret"></b>
                </a>
                <ul class="dropdown-menu">
                    <li> <a href="form-general.html">General</a>  </li>
                    <li> <a href="form-validation.html">Validation</a>  </li>
                    <li> <a href="form-wysiwyg.html">WYSIWYG</a>  </li>
                    <li> <a href="form-wizard.html">Wizard &amp; File Upload</a>  </li>
                </ul>
            </li>
            -->
        </ul><!-- /.nav -->
    </div>
</nav><!-- /.navbar -->

<!-- header.head -->
<header class="head">
    <div class="search-bar">
        <a data-original-title="Show/Hide Menu" data-placement="bottom" data-tooltip="tooltip" class="accordion-toggle btn btn-primary btn-sm visible-xs" data-toggle="collapse" href="#menu" id="menu-toggle">
            <i class="fa fa-expand"></i>
        </a>
        <form class="main-search">
            <div class="input-group">
                <input type="text" class="input-small form-control" placeholder="Live Search ...">
                <span class="input-group-btn">
                                    <button class="btn btn-primary btn-sm text-muted" type="button"><i class="fa fa-search"></i></button>
                                </span>
            </div>
        </form>
    </div>

    <!-- ."main-bar -->
    <div class="main-bar">
        <h3>${html_title!((sri.screenUrlInfo.targetScreen.getDefaultMenuName())!"Page")}</h3>
    </div><!-- /.main-bar -->
</header>

<!-- end header.head -->
