package fi.jyu.ties4520.f8;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.*;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;

/**
 * Task F8: Jena SPARQL + Rule-based reasoning endpoint on top of XHTML+RDFa.
 *
 * POST parameters:
 *   - rdfaUrl : URL of XHTML/HTML+RDFa file
 *   - rules   : Jena rule syntax (optional)
 *   - query   : SPARQL query (SELECT / ASK / CONSTRUCT / DESCRIBE)
 */
public class JenaReasoningServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // Render SELECT results as an HTML table
    private String resultSetToHTML(ResultSet results) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table border='1' cellspacing='0' cellpadding='4'>");

        // Header row
        sb.append("<tr>");
        for (String var : results.getResultVars()) {
            sb.append("<th>").append(escapeHtml(var)).append("</th>");
        }
        sb.append("</tr>");

        // Data rows
        while (results.hasNext()) {
            QuerySolution sol = results.nextSolution();
            sb.append("<tr>");
            for (String var : results.getResultVars()) {
                String cell = "";
                if (sol.contains(var) && sol.get(var) != null) {
                    cell = sol.get(var).toString();
                }
                sb.append("<td>").append(escapeHtml(cell)).append("</td>");
            }
            sb.append("</tr>");
        }

        sb.append("</table>");
        return sb.toString();
    }

    // Simple HTML escaping (enough for this assignment)
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // For convenience, redirect GET to the HTML client page
        resp.sendRedirect(req.getContextPath() + "/f8_client.html");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=UTF-8");

        PrintWriter out = response.getWriter();

        String rdfaUrl = request.getParameter("rdfaUrl");
        String rulesStr = request.getParameter("rules");
        String queryStr = request.getParameter("query");

        out.println("<!DOCTYPE html>");
        out.println("<html><head><meta charset='UTF-8'><title>Task F8 Result</title></head><body>");
        out.println("<h2>Task F8 - SPARQL + Rule-based Reasoning Result</h2>");

        try {
            if (rdfaUrl == null || rdfaUrl.trim().isEmpty()) {
                out.println("<p style='color:red'><b>Error:</b> RDFa URL is missing.</p>");
                out.println("</body></html>");
                return;
            }
            if (queryStr == null || queryStr.trim().isEmpty()) {
                out.println("<p style='color:red'><b>Error:</b> SPARQL query is missing.</p>");
                out.println("</body></html>");
                return;
            }

            rdfaUrl = rdfaUrl.trim();
            queryStr = queryStr.trim();

            out.println("<p><b>RDFa URL:</b> " + escapeHtml(rdfaUrl) + "</p>");

            // --- 1) Parse RDFa into a base model ---------------------------



            Model baseModel = ModelFactory.createDefaultModel();

            // Register java-rdfa classic readers
            baseModel.setReaderClassName("XHTML", "net.rootdev.javardfa.jena.RDFaReader");
            baseModel.setReaderClassName("HTML",  "net.rootdev.javardfa.jena.RDFaReader");
            
            // Force use of the java-rdfa reader (bypass RIOT)
            RDFReader reader = baseModel.getReader("XHTML");
            reader.read(baseModel, rdfaUrl);
            

            out.println("<p>Parsed RDFa; base model has "
                    + baseModel.size() + " triples.</p>");

            // --- 2) Apply rules (if any) to get an inference model ----------

            Model modelToQuery = baseModel;

            if (rulesStr != null && !rulesStr.trim().isEmpty()) {
                rulesStr = rulesStr.trim();

                out.println("<h3>Rules</h3>");
                out.println("<pre>" + escapeHtml(rulesStr) + "</pre>");

                // Parse Jena rules
                java.util.List<Rule> rules = Rule.parseRules(rulesStr);
                Reasoner reasoner = new GenericRuleReasoner(rules);
                reasoner.setDerivationLogging(false);

                InfModel infModel = ModelFactory.createInfModel(reasoner, baseModel);
                modelToQuery = infModel;

                out.println("<p>Reasoning applied. Total triples in inferred model: "
                        + modelToQuery.size() + "</p>");
            } else {
                out.println("<p>No rules supplied – querying base RDFa model only.</p>");
            }

            // --- 3) Execute SPARQL query -----------------------------------

            out.println("<h3>SPARQL Query</h3>");
            out.println("<pre>" + escapeHtml(queryStr) + "</pre>");

            Query query = QueryFactory.create(queryStr);

            try (QueryExecution qexec = QueryExecutionFactory.create(query, modelToQuery)) {

                if (query.isSelectType()) {
                    ResultSet rs = qexec.execSelect();
                    rs = ResultSetFactory.copyResults(rs); // so we can iterate twice if needed
                    out.println("<h3>SELECT results</h3>");
                    out.println(resultSetToHTML(rs));

                } else if (query.isAskType()) {
                    boolean answer = qexec.execAsk();
                    out.println("<h3>ASK result</h3>");
                    out.println("<p><b>" + answer + "</b></p>");

                } else if (query.isConstructType()) {
                    Model m = qexec.execConstruct();
                    out.println("<h3>CONSTRUCT graph (Turtle)</h3>");
                    out.println("<pre>");
                    m.write(out, "TURTLE");
                    out.println("</pre>");

                } else if (query.isDescribeType()) {
                    Model m = qexec.execDescribe();
                    out.println("<h3>DESCRIBE graph (Turtle)</h3>");
                    out.println("<pre>");
                    m.write(out, "TURTLE");
                    out.println("</pre>");

                } else {
                    out.println("<p style='color:red'><b>Error:</b> Unsupported query type.</p>");
                }
            }

        } catch (QueryParseException e) {
            out.println("<p style='color:red'><b>SPARQL parse error:</b> "
                    + escapeHtml(e.getMessage()) + "</p>");
            e.printStackTrace();

        } catch (Exception e) {
            out.println("<p style='color:red'><b>Error:</b> "
                    + escapeHtml(e.getMessage()) + "</p>");
            e.printStackTrace();
        }

        out.println("<p><a href=\"" + request.getContextPath() +
                "/f8_client.html\">Back to client</a></p>");
        out.println("</body></html>");
    }
}
