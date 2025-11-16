package fi.jyu.ties4520.f8;

import java.io.*;
import java.net.URL;
import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.jena.rdf.model.*;
import org.apache.jena.query.*;
import org.apache.jena.reasoner.*;
import org.apache.jena.reasoner.rulesys.*;

public class JenaReasoningServlet extends HttpServlet {

    // Convert SELECT resultset to an HTML table
    private String resultSetToHTML(ResultSet results) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table border='1'>");

        // Header row
        sb.append("<tr>");
        for (String var : results.getResultVars()) {
            sb.append("<th>").append(var).append("</th>");
        }
        sb.append("</tr>");

        // Data rows
        while (results.hasNext()) {
            QuerySolution sol = results.next();
            sb.append("<tr>");
            for (String var : results.getResultVars()) {
                RDFNode node = sol.get(var);
                sb.append("<td>").append(node == null ? "" : node.toString()).append("</td>");
            }
            sb.append("</tr>");
        }

        sb.append("</table>");
        return sb.toString();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        String rdfaUrl = req.getParameter("rdfaUrl");
        System.out.println("DEBUG RDFa URL: '" + rdfaUrl + "'");

        String rulesText = req.getParameter("rules");
        String sparql = req.getParameter("query");

        out.println("<html><head><title>Task F8 Results</title></head><body>");

        try {

            // -----------------------------
            // 1) Load XHTML+RDFa into model
            // -----------------------------
            Model base = ModelFactory.createDefaultModel();

            try (InputStreamReader reader = new InputStreamReader(
                    new URL(rdfaUrl).openStream(), "UTF-8")) {

                base.read(reader, rdfaUrl, "RDFA");
            }

            // -----------------------------
            // 2) Load Jena rules
            // -----------------------------
            java.util.List<Rule> rules = Rule.parseRules(rulesText);

            // -----------------------------
            // 3) Apply rule-based reasoning
            // -----------------------------
            Reasoner reasoner = new GenericRuleReasoner(rules);
            InfModel infModel = ModelFactory.createInfModel(reasoner, base);

            // -----------------------------
            // 4) Prepare SPARQL query
            // -----------------------------
            Query query = QueryFactory.create(sparql);
            QueryExecution qexec = QueryExecutionFactory.create(query, infModel);

            out.println("<h2>SPARQL Query Results</h2>");

            // -----------------------------
            // 5) Execute the query
            // -----------------------------
            if (query.isSelectType()) {

                ResultSet results = qexec.execSelect();
                out.println(resultSetToHTML(results));

            } else if (query.isAskType()) {

                boolean result = qexec.execAsk();
                out.println("<p><b>ASK Result:</b> " + result + "</p>");

            } else if (query.isConstructType()) {

                Model m = qexec.execConstruct();
                out.println("<pre>");
                m.write(out, "TURTLE");
                out.println("</pre>");

            } else if (query.isDescribeType()) {

                Model m = qexec.execDescribe();
                out.println("<pre>");
                m.write(out, "TURTLE");
                out.println("</pre>");
            }

        } catch (Exception e) {

            out.println("<p style='color:red'><b>Error:</b> " + e.getMessage() + "</p>");
            e.printStackTrace();
        }

        out.println("</body></html>");
    }
}
