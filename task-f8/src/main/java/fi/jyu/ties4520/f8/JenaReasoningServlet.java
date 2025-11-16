package fi.jyu.ties4520.f8;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

import org.apache.jena.rdf.model.*;
import org.apache.jena.query.*;
import org.apache.jena.reasoner.*;
import org.apache.jena.rulesys.*;

@WebServlet("/jena")
public class JenaReasoningServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        String rdfaUrl  = req.getParameter("rdfaUrl");
        String rulesTxt = req.getParameter("rules");
        String sparql   = req.getParameter("query");

        out.println("<html><body>");

        try {
            // enable RDFa parsing
            Class.forName("net.rootdev.javardfa.RDFaReader");

            Model base = ModelFactory.createDefaultModel();
            base.read(rdfaUrl, "XHTML");

            Model queryModel = base;

            // Apply rules if provided
            if (rulesTxt != null && !rulesTxt.trim().isEmpty()) {
                java.util.List<Rule> rules = Rule.parseRules(rulesTxt);
                Reasoner reasoner = new GenericRuleReasoner(rules);
                queryModel = ModelFactory.createInfModel(reasoner, base);
            }

            Query query = QueryFactory.create(sparql);

            try (QueryExecution qexec = QueryExecutionFactory.create(query, queryModel)) {

                if (query.isSelectType()) {
                    ResultSet results = qexec.execSelect();
                    out.println("<pre>");
                    results.forEachRemaining(r -> out.println(r.toString()));
                    out.println("</pre>");

                } else if (query.isAskType()) {
                    out.println("<p>ASK: " + qexec.execAsk() + "</p>");

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
            }

        } catch (Exception e) {
            out.println("<pre>");
            e.printStackTrace(out);
            out.println("</pre>");
        }

        out.println("</body></html>");
    }
}
