package controllers;

import play.*;
import play.mvc.*;
import play.libs.*;
import play.libs.F.*;

import views.html.*;

import java.net.*;
import java.util.*;
import org.w3c.dom.*;

public class Application extends Controller {

    private static String BHL_URL = "http://www.biodiversitylibrary.org/api2/httpquery.ashx";
  
    public static Result index() {
        return ok(index.render());
    }

    public static Result query(String page) {
        String errorMessage = "";
        String commonsString = "";

        // For now, assume that we have a page id. 
        HashMap<String, String> pageMetadata = new HashMap<String, String>(getBHLPageMetadata(page));
        HashMap<String, String> itemMetadata = new HashMap<String, String>(getBHLItemMetadata(pageMetadata.get("//ItemID")));
        HashMap<String, String> titleMetadata = new HashMap<String, String>(getBHLTitleMetadata(itemMetadata.get("//PrimaryTitleID")));
        

        return ok(result.render(page, errorMessage, pageMetadata, itemMetadata, titleMetadata, commonsString));
    }

    private static Map<String, String> getBHLPageMetadata(String page_id) {
        String bhl_api_key = System.getenv("BHL_API_KEY");

        Promise<WS.Response> promise = 
            WS  .url(BHL_URL)
                .setQueryParameter("op", "GetPageMetadata")
                .setQueryParameter("pageid", page_id)
                .setQueryParameter("ocr", "t")
                .setQueryParameter("names", "t")
                .setQueryParameter("apikey", bhl_api_key)
                .get();

        return getBHLProperties(promise);
    }

    private static Map<String, String> getBHLItemMetadata(String item_id) {
        String bhl_api_key = System.getenv("BHL_API_KEY");

        Promise<WS.Response> promise = 
            WS  .url(BHL_URL)
                .setQueryParameter("op", "GetItemMetadata")
                .setQueryParameter("itemid", item_id)
                .setQueryParameter("pages", "f")
                .setQueryParameter("apikey", bhl_api_key)
                .get();

        return getBHLProperties(promise);
    }

    private static Map<String, String> getBHLTitleMetadata(String title_id) {
        String bhl_api_key = System.getenv("BHL_API_KEY");

        Promise<WS.Response> promise = 
            WS  .url(BHL_URL)
                .setQueryParameter("op", "GetTitleMetadata")
                .setQueryParameter("titleid", title_id)
                .setQueryParameter("items", "f")
                .setQueryParameter("apikey", bhl_api_key)
                .get();

        return getBHLProperties(promise);
    }

    private static Map<String, String> getBHLProperties(Promise<WS.Response> promise) {
        while(promise.get() == null)
            ;

        WS.Response response = promise.get();
        HashMap<String, String> results = new HashMap<String, String>();

        // TODO: check response.getStatus() == 200
        if(response.getStatus() != 200) {
            results.put("error", "Unable to retrieve: status message of HTTP " + response.getStatus() + " returned.");
            return results;
        }

        Document dom = response.asXml();
        if(dom == null) {
            results.put("error", "Unable to retrieve: retrieved content is not valid XML");
            results.put("body", response.getBody());

            return results;
        }

        if(!XPath.selectText("/Response/Status", dom).equals("ok")) {
            results.put("error", "Unable to retrieve: BHL returned a status of '" + XPath.selectText("/Response/Status", dom)  + "'");
            results.put("body", response.getBody());

            return results;

        }

        dom.normalize();
        for(Node n: XPath.selectNodes("/Response/Result/*", dom)) {
            addTextNodes(results, n, "/");
        }

        return results;
    }

    private static void addTextNodes(HashMap<String, String> results, Node root, String parentNode) {
        for(int x = 0; x < root.getChildNodes().getLength(); x++) {
            Node n = root.getChildNodes().item(x);

            String name = parentNode + "/" + root.getNodeName();
            if(x > 0) name += String.format("[%d]", x);

            if(n.getNodeType() == Node.TEXT_NODE) {
                results.put(name, n.getNodeValue());
            } else {
                addTextNodes(results, n, name);
            }
        }
    }

    // Yes, I know this should be in views. I don't care.
    public static String formatValue(String value) {
        if(value == null) return "<em>Undefined</em>";

        try {
            new URL(value);
            return "<a href='" + value + "'>" + value + "</a>";
        } catch(MalformedURLException e) {}

        return value;
    }
}
