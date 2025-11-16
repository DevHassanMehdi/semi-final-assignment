package fi.jyu.ties4520.f7;

import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.*;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.http.HTTPRepository;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class Rdf4jSparqlEndpoint extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // Default values required by the assignment
    private static final String DEFAULT_REPO_URL = "http://localhost:8080/rdf4j-server";
    private static final String DEFAULT_REPO_ID  = "ties4520";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // For simplicity, just redirect GET to POST to support direct form submissions
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=UTF-8");

        String repoUrl = paramOrDefault(request.getParameter("repoUrl"), DEFAULT_REPO_URL);
        String repoId  = paramOrDefault(request.getParameter("repoId"),  DEFAULT_REPO_ID);
        String queryStr = request.getParameter("query");

        PrintWriter out = response.getWriter();

        out.println("<!DOCTYPE html>");
        out.println("<html><head>");
        out.println("<meta charset='UTF-8'/>");
        out.println("<title>RDF4J SPARQL Endpoint (Task F7)</title>");
        out.println("<style>");
        out.println("body { font-family: Arial, sans-serif; margin: 20px; }");
        out.println("table { border-collapse: collapse; margin-top: 10px; }");
        out.println("th, td { border: 1px solid #ccc; padding: 4px 8px; }");
        out.println("th { background: #eee; }");
        out.println(".error { color: red; }");
        out.println(".info { color: #555; }");
        out.println("</style>");
        out.println("</head><body>");

        out.println("<h1>RDF4J SPARQL Endpoint (Task F7)</h1>");

        // If no query provided, just show a message (client page should handle the UI)
        if (queryStr == null || queryStr.trim().isEmpty()) {
            out.println("<p class='info'>No SPARQL query provided. Use the client page to submit a query.</p>");
            out.println("</body></html>");
            return;
        }

        out.println("<h2>Connection</h2>");
        out.println("<p class='info'>Repository URL: " + escapeHtml(repoUrl) +
                "<br/>Repository ID: " + escapeHtml(repoId) + "</p>");

        Repository repo = null;
        RepositoryConnection conn = null;

        try {
            // Connect to remote RDF4J repository
            repo = new HTTPRepository(repoUrl, repoId);
            repo.init();
            conn = repo.getConnection();

            // Prepare generic SPARQL query (RDF4J detects type automatically)
            Query q = conn.prepareQuery(QueryLanguage.SPARQL, queryStr);

            out.println("<h2>SPARQL Query</h2>");
            out.println("<pre>" + escapeHtml(queryStr) + "</pre>");

            if (q instanceof TupleQuery) {
                out.println("<h2>Result (SELECT)</h2>");
                executeSelectQuery((TupleQuery) q, out);
            } else if (q instanceof GraphQuery) {
                out.println("<h2>Result (CONSTRUCT/DESCRIBE)</h2>");
                executeGraphQuery((GraphQuery) q, out);
            } else if (q instanceof BooleanQuery) {
                out.println("<h2>Result (ASK)</h2>");
                executeBooleanQuery((BooleanQuery) q, out);
            } else {
                out.println("<p class='error'>Unsupported query type.</p>");
            }

        } catch (MalformedQueryException mq) {
            out.println("<p class='error'><strong>Malformed query:</strong> "
                    + escapeHtml(mq.getMessage()) + "</p>");
        } catch (QueryEvaluationException qe) {
            out.println("<p class='error'><strong>Query evaluation error:</strong> "
                    + escapeHtml(qe.getMessage()) + "</p>");
        } catch (Exception e) {
            out.println("<p class='error'><strong>Error:</strong> "
                    + escapeHtml(e.getMessage()) + "</p>");
        } finally {
            if (conn != null) {
                conn.close();
            }
            if (repo != null) {
                repo.shutDown();
            }
            out.println("</body></html>");
            out.flush();
        }
    }

    private void executeSelectQuery(TupleQuery tupleQuery, PrintWriter out)
            throws QueryEvaluationException {

        try (TupleQueryResult result = tupleQuery.evaluate()) {
            List<String> bindingNames = result.getBindingNames();

            out.println("<table>");
            // Header row
            out.println("<tr>");
            for (String name : bindingNames) {
                out.println("<th>" + escapeHtml(name) + "</th>");
            }
            out.println("</tr>");

            // Data rows
            while (result.hasNext()) {
                BindingSet bs = result.next();
                out.println("<tr>");
                for (String name : bindingNames) {
                    Value v = bs.getValue(name);
                    out.println("<td>" + (v == null ? "" : escapeHtml(v.stringValue())) + "</td>");
                }
                out.println("</tr>");
            }
            out.println("</table>");
        }
    }

    private void executeGraphQuery(GraphQuery graphQuery, PrintWriter out)
            throws QueryEvaluationException {

        try (GraphQueryResult result = graphQuery.evaluate()) {
            out.println("<table>");
            out.println("<tr><th>Subject</th><th>Predicate</th><th>Object</th></tr>");

            while (result.hasNext()) {
                Statement st = result.next();
                out.println("<tr>");
                out.println("<td>" + escapeHtml(st.getSubject().stringValue()) + "</td>");
                out.println("<td>" + escapeHtml(st.getPredicate().stringValue()) + "</td>");
                out.println("<td>" + escapeHtml(st.getObject().stringValue()) + "</td>");
                out.println("</tr>");
            }

            out.println("</table>");
        }
    }

    private void executeBooleanQuery(BooleanQuery booleanQuery, PrintWriter out)
            throws QueryEvaluationException {

        boolean value = booleanQuery.evaluate();
        out.println("<p><strong>ASK result:</strong> " + value + "</p>");
    }

    private static String paramOrDefault(String param, String defaultValue) {
        if (param == null || param.trim().isEmpty()) {
            return defaultValue;
        }
        return param.trim();
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
    }
}
