package com.xin.aoc.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.xin.aoc.form.ProblemForm;
import com.xin.aoc.form.UserForm;
import com.xin.aoc.mapper.SubmissionMapper;
import com.xin.aoc.model.Problem;
import com.xin.aoc.model.Submission;
import com.xin.aoc.model.UserInfo;
import com.xin.aoc.service.*;

import com.xin.aoc.service.impl.CompilerServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.sf.jsqlparser.expression.StringValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Controller
public class ProblemController {
    private Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private ProblemService problemService;
    @Autowired
    private IndexController indexController;
    @Autowired
    private RecordService recordService;
    @Autowired
    private CompilerService compilerService;
    @Autowired
    private SubmissionService submissionService;
    @Autowired
    private SubmissionMapper submissionMapper;


    @GetMapping(value="/problems")
    public String add(@RequestParam(required=false,value="id") int id, Model model){
        Problem problem = problemService.getProblem(id);

        if(problem==null){
            logger.info("!!contest");
            return "redirect:/";
        }
        model.addAttribute("problemInfo", problem);
        return "problem/problems";
    }



    @GetMapping(value="/user/submit")
    public String sub(
                         @RequestParam(required=false,value="id") int problemId,
                         @RequestParam(required=false,value="msg") String msg,
                         @RequestParam(required=false,value="result") String result,
                         Model model, HttpServletRequest request,
                         RedirectAttributes redirectAttributes){
        HttpSession session = request.getSession();
        UserInfo user = (UserInfo)request.getSession().getAttribute("login_user");
        logger.info("!!proid"+problemId);
        String lastSubmission = submissionMapper.getLastSubmission(problemId, user.getUserId());
        model.addAttribute("lastSubmission", lastSubmission);
        Problem problem = problemService.getProblem(problemId);
        model.addAttribute("problemInfo", problem);
        model.addAttribute("result", result);
        model.addAttribute("msg", msg);
        return "user/submit";
    }

    @PostMapping(value="/user/submit")
    public String submit(@RequestParam(value="file") MultipartFile file,
                         @RequestParam(required=false,value="code") String word_code,
                         @RequestParam(required=false,value="id") int id,
                         Model model, HttpServletRequest request,
                         HttpServletResponse response){


        Problem problem = problemService.getProblem(id);
        HttpSession session = request.getSession();
        UserInfo user = (UserInfo)request.getSession().getAttribute("login_user");
        model.addAttribute("problemInfo", problem);

        String code="";
        if((file==null && word_code==null) || user==null){
            return "redirect:/user/submit";
        }

        if(!Objects.equals(word_code, "")){
            code=word_code;
        }else {
            if (!file.isEmpty()) {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        code += line;
                        code += '\n';
                    }
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        String name="";
        String output="";
        if(user!=null){
             name = "test"+user.getUserId();
        }else{
            return  "redirect:/login";
        }
        output = compilerService.compile(name,code);
        String msg="";
        String result="";
        if(output.length() == 0){
            output = compilerService.execute(name, problem.getInput());
            msg="";
        }else{
            msg=output;
        }

        logger.info("code: "+code);
        logger.info("output "+output);

        String time=new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new java.util.Date());
        int status=0;
        if(output.equals(problem.getAnswer()) && user!=null){
            result="Your answer is correct!";
            if(recordService.exist(user.getUserId(),problem.getProblemId())<1) {
                problemService.addScore(user.getUserId());
                recordService.addRecord(time, user.getUserId(),problem.getProblemId());
                status = 1;
            }
        }else{
           result="Your answer is incorrect, please try again.";
        }
        model.addAttribute("msg",msg);
        model.addAttribute("result",result);
        model.addAttribute("lastSubmission",code);
        submissionService.update(problem.getProblemId(),user.getUserId(), code, time, status, problem.getTitle());
        return "user/submit";
    }


    @RequestMapping(value = "/user/submissions")
    public String search(@RequestParam(required = false, defaultValue = "1", value = "page")
                         Integer page,
                         @RequestParam(required = false,  value = "key") String key,
                         @RequestParam(required = false,  value = "id") int problemId,
                         Model model, HttpServletRequest request) {
        Problem problem = problemService.getProblem(problemId);
        model.addAttribute("problemInfo", problem);
        if (page == null || page <= 0) page = 1;
        int size = 8;
        PageHelper.startPage(page, size);
        UserInfo user = (UserInfo)request.getSession().getAttribute("login_user");

        List<Submission> submissions = submissionService.getSubmissionsByUserId(user.getUserId(),problemId);
        logger.info(submissions.toString()+"!!!!");
        PageInfo<Submission> pageInfo = new PageInfo<Submission>(submissions, size);
        model.addAttribute("submissions", pageInfo);

        return "user/submissions";
    }
    @RequestMapping(value = "/user/code")
    public String codes(
                         @RequestParam(required = false,  value = "problem_id") int problemId,
                         @RequestParam(required = false,  value = "user_id") int userId,
                         @RequestParam(required = false,  value = "cur_date") String curDate,
                         Model model, HttpServletRequest request) {

        Submission submission = submissionService.getSubmission(userId,problemId,curDate);
        logger.info("!!!sumbisiion"+problemId);
        model.addAttribute("submission", submission);
        Problem problem = problemService.getProblem(problemId);
        model.addAttribute("problemInfo", problem);
        return "user/code";
    }
}