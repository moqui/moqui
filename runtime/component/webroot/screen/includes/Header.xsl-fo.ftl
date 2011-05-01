<fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format" font-family="Helvetica, sans-serif" font-size="10pt">
    <fo:layout-master-set>
        <#-- Letter -->
        <fo:simple-page-master master-name="letter-portrait" page-width="8.5in" page-height="11in"
                margin-top="0.5in" margin-bottom="0.5in" margin-left="0.5in" margin-right="0.5in">
            <fo:region-body margin-top="1in" margin-bottom="0.5in"/>
            <fo:region-before extent="1in"/>
            <fo:region-after extent="0.5in"/>
        </fo:simple-page-master>
        <fo:simple-page-master master-name="letter-landscape" page-width="11in" page-height="8.5in"
                margin-top="0.5in" margin-bottom="0.5in" margin-left="0.5in" margin-right="0.5in">
            <fo:region-body margin-top="1in" margin-bottom="0.5in"/>
            <fo:region-before extent="1in"/>
            <fo:region-after extent="0.5in"/>
        </fo:simple-page-master>

        <#-- Double-Letter (11x17) -->
        <fo:simple-page-master master-name="11x17-portrait" page-width="11in" page-height="17in"
                margin-top="0.5in" margin-bottom="0.5in" margin-left="0.5in" margin-right="0.5in">
            <fo:region-body margin-top="1in" margin-bottom="0.5in"/>
            <fo:region-before extent="1in"/>
            <fo:region-after extent="0.5in"/>
        </fo:simple-page-master>
        <fo:simple-page-master master-name="11x17-landscape" page-width="17in" page-height="11in"
                margin-top="0.5in" margin-bottom="0.5in" margin-left="0.5in" margin-right="0.5in">
            <fo:region-body margin-top="1in" margin-bottom="0.5in"/>
            <fo:region-before extent="1in"/>
            <fo:region-after extent="0.5in"/>
        </fo:simple-page-master>

        <#-- ISO 216 -->
        <fo:simple-page-master master-name="iso216-portrait" page-width="210mm" page-height="297mm"
                margin-top="20mm" margin-bottom="20mm" margin-left="15mm" margin-right="15mm">
            <fo:region-body margin-top="30mm" margin-bottom="12mm"/>
            <fo:region-before extent="25mm"/>
            <fo:region-after extent="12mm" />
        </fo:simple-page-master>
        <fo:simple-page-master master-name="iso216-landscape" page-width="210mm" page-height="297mm"
                margin-top="15mm" margin-bottom="15mm" margin-left="20mm" margin-right="20mm">
            <fo:region-body margin-top="25mm" margin-bottom="12mm"/>
            <fo:region-before extent="25mm"/>
            <fo:region-after extent="12mm" />
        </fo:simple-page-master>
    </fo:layout-master-set>

    <fo:page-sequence master-reference="letter-portrait">
        <fo:static-content flow-name="xsl-region-before">
            <fo:block font-size="14pt" text-align="center" margin-bottom="14pt">
                ${documentTitle?if_exists?xml}
            </fo:block>
        </fo:static-content>
        <fo:static-content flow-name="xsl-region-after" font-size="8pt">
            <fo:block text-align="center" border-top="thin solid black">Made on Moqui</fo:block>
            <fo:block text-align="center">- <fo:page-number/> -</fo:block>
        </fo:static-content>

        <fo:flow flow-name="xsl-region-body">
